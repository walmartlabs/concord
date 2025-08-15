package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.LogTags;
import com.walmartlabs.concord.sdk.MapUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.client.ConcordTaskParams.*;

public class ConcordTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(ConcordTaskCommon.class);

    private static final long DEFAULT_KILL_TIMEOUT = 10000;
    private static final long DEFAULT_POLL_DELAY = 5000;

    private static final int MAX_EXECUTOR_THREADS = 20;

    private static final Set<String> FAILED_STATUSES;

    static {
        FAILED_STATUSES = new HashSet<>();
        FAILED_STATUSES.add(ProcessEntry.StatusEnum.FAILED.toString());
        FAILED_STATUSES.add(ProcessEntry.StatusEnum.CANCELLED.toString());
        FAILED_STATUSES.add(ProcessEntry.StatusEnum.TIMED_OUT.toString());
    }

    private final ExecutorService executor = new ThreadPoolExecutor(1, MAX_EXECUTOR_THREADS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    private final String sessionToken;
    private final ApiClientFactory apiClientFactory;
    private final UUID currentProcessId;
    private final String currentOrgName;
    private final Path workDir;
    private final boolean globalDebug;
    private final boolean dryRun;

    public ConcordTaskCommon(String sessionToken, ApiClientFactory apiClientFactory, UUID currentProcessId, String currentOrgName, Path workDir, boolean globalDebug, boolean dryRun) {
        this.sessionToken = sessionToken;
        this.apiClientFactory = apiClientFactory;
        this.currentProcessId = currentProcessId;
        this.currentOrgName = currentOrgName;
        this.workDir = workDir;
        this.globalDebug = globalDebug;
        this.dryRun = dryRun;
    }

    public TaskResult execute(ConcordTaskParams in) throws Exception {
        Action action = in.action();
        return switch (action) {
            case START -> startChildProcess((StartParams) in);
            case STARTEXTERNAL -> startExternalProcess((StartExternalParams) in);
            case FORK -> fork((ForkParams) in);
            case KILL -> {
                kill((KillParams) in);
                yield TaskResult.success();
            }
            case CREATEAPIKEY -> createApiKey((CreateOrUpdateApiKeyParams) in);
            case CREATEORUPDATEAPIKEY -> createOrUpdateApiKey((CreateOrUpdateApiKeyParams) in);
        };
    }

    public List<ProcessEntry> listSubProcesses(ListSubProcesses in) throws Exception {
        UUID instanceId = in.instanceId();
        Set<String> tags = in.tags();

        return withClient(client -> {
            ProcessApi api = new ProcessApi(client);

            return api.listSubprocesses(instanceId, tags);
        });
    }

    public String suspendForCompletion(List<UUID> ids) throws Exception {
        TaskResult result = suspend(new ResumePayload(null, null, false, ids, false), false);
        if (!(result instanceof TaskResult.SuspendResult)) {
            throw new RuntimeException("Invalid result type. This is most likely a bug.");
        }
        return ((TaskResult.SuspendResult) result).eventName();
    }

    public <T> Map<String, T> waitForCompletion(List<UUID> ids, long timeout, Function<ProcessEntry, T> processor) {
        Map<String, T> result = new ConcurrentHashMap<>();

        ids.parallelStream().forEach(id -> {
            log.info("Waiting for {}", LogTags.instanceId(id));

            long t1 = System.currentTimeMillis();
            while (true) {
                try {
                    ProcessEntry e = ClientUtils.withRetry(3, 1000,
                            () -> withClient(client -> {
                                ProcessV2Api api = new ProcessV2Api(client);
                                return api.getProcess(id, Collections.emptySet());
                            }));

                    ProcessEntry.StatusEnum s = e.getStatus();

                    if (isFinalStatus(s)) {
                        T t = processor.apply(e);
                        if (t != null) {
                            result.put(id.toString(), t);
                        }
                        break;
                    } else {
                        long t2 = System.currentTimeMillis();
                        if (timeout > 0) {
                            long dt = t2 - t1;
                            if (dt >= timeout) {
                                throw new TimeoutException("Timeout waiting for " + id + ": " + dt);
                            }
                        }

                        Thread.sleep(DEFAULT_POLL_DELAY);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return result;
    }

    public void kill(KillParams in) throws Exception {
        List<UUID> instanceIds = in.ids();
        if (instanceIds.isEmpty()) {
            log.warn("kill: no process IDs specified, nothing to do.");
            return;
        }

        for (UUID id : instanceIds) {
            withClient(client -> {
                ProcessApi api = new ProcessApi(client);
                api.kill(id);
                return null;
            });

            if (in.sync()) {
                waitForCompletion(Collections.singletonList(id), DEFAULT_KILL_TIMEOUT, Function.identity());
            }
        }
    }

    public TaskResult createApiKey(CreateOrUpdateApiKeyParams in) throws Exception {
        return withClient(in.baseUrl(), in.apiKey(), client -> {
            log.info("Creating a new API key in {}", client.getBaseUri());

            UUID userId = assertUserId(client, in);

            String keyName = in.name();
            if (keyName != null) {
                ApiKeysApi api = new ApiKeysApi(client);
                List<ApiKeyEntry> existingKeys = api.listUserApiKeys(userId);
                Optional<ApiKeyEntry> maybeExistingKey = existingKeys.stream().filter(k -> k.getName().equals(keyName)).findFirst();
                if (maybeExistingKey.isPresent()) {
                    if (in.ignoreExisting()) {
                        log.info("API key '{}' already exists, nothing to do.", keyName);
                        ApiKeyEntry existingKey = maybeExistingKey.get();
                        return TaskResult.success()
                                .value("id", existingKey.getId())
                                .value("expiredAt", Optional.ofNullable(existingKey.getExpiredAt()).map(OffsetDateTime::toString).orElse(null));
                    } else {
                        throw new IllegalArgumentException("API key '" + keyName + "' already exists.");
                    }
                }
            }

            ApiKeysApi apiKeysApi = new ApiKeysApi(client);
            CreateApiKeyResponse response = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                    .name(keyName)
                    .userId(userId)
                    .userDomain(in.userDomain())
                    .userType(in.userType())
                    .key(in.key()));

            return TaskResult.success()
                    .value("id", response.getId())
                    .value("name", response.getName())
                    .value("key", response.getKey());
        });
    }

    public TaskResult createOrUpdateApiKey(CreateOrUpdateApiKeyParams in) throws Exception {
        return withClient(in.baseUrl(), in.apiKey(), client -> {
            log.info("Creating or updating an API key in {}", client.getBaseUri());

            UUID userId = assertUserId(client, in);

            ApiKeysV2Api apiKeysApi = new ApiKeysV2Api(client);
            CreateApiKeyResponse response = apiKeysApi.createOrUpdateUserApiKey(new CreateApiKeyRequest()
                    .name(in.name())
                    .userId(userId)
                    .userDomain(in.userDomain())
                    .userType(in.userType())
                    .key(in.key()));

            return TaskResult.success()
                    .value("id", response.getId())
                    .value("name", response.getName())
                    .value("key", response.getKey())
                    .value("result", response.getResult().toString());
        });
    }

    private UUID assertUserId(ApiClient client, CreateOrUpdateApiKeyParams in) throws ApiException {
        UUID userId = in.userId();
        if (userId == null) {
            String username = in.username();
            if (username == null) {
                throw new IllegalArgumentException("User ID or user name is required");
            }

            UsersApi usersApi = new UsersApi(client);
            UserEntry user = usersApi.findByUsername(username);
            if (user == null) {
                throw new IllegalArgumentException("User '" + username + "' not found.");
            }
            userId = user.getId();
        }
        return userId;
    }

    public Map<String, Map<String, Object>> getOutVars(String baseUrl, String apiKey, List<UUID> ids, long timeout) {
        return waitForCompletion(ids, timeout, p -> {
            try {
                return getOutVars(baseUrl, apiKey, p.getInstanceId());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private TaskResult startExternalProcess(StartExternalParams in) throws Exception {
        if (in.sync() && in.suspendRaw()) {
            log.warn("Input parameter '{}' ignored for {} action", StartParams.SUSPEND_KEY, Action.STARTEXTERNAL);
        }

        return start(in, null);
    }

    private TaskResult startChildProcess(StartParams in) throws Exception {
        return start(in, currentProcessId);
    }

    private TaskResult start(StartParams in, UUID parentInstanceId) throws Exception {
        Path archive = archivePayload(workDir, in);
        String project = in.project();
        if (project == null && archive == null) {
            throw new IllegalArgumentException("'" + StartParams.PAYLOAD_KEY + "' and/or '" + StartParams.PROJECT_KEY + "' are required");
        }

        Map<String, Object> input = new HashMap<>();

        if (archive != null) {
            input.put("archive", archive);
        }

        Map<String, Object> req = createRequest(in);

        ObjectMapper om = new ObjectMapper();
        input.put("request", om.writeValueAsBytes(req));

        String org = getOrg(in);
        addIfNotNull(input, "org", org);
        addIfNotNull(input, "project", project);

        addIfNotNull(input, "repo", in.repo());
        addIfNotNull(input, "repoBranchOrTag", in.repoBranchOrTag());
        addIfNotNull(input, "repoCommitId", in.repoCommitId());

        Collection<Object> attachments = in.attachments();
        processAttachments(attachments).forEach((d, p) -> addIfNotNull(input, d, p));

        addIfNotNull(input, "startAt", in.startAt());

        addIfNotNull(input, "parentInstanceId", parentInstanceId);

        boolean sync = in.sync();
        boolean debug = in.debug(globalDebug);
        if (parentInstanceId != null) {
            if (debug) {
                log.info("Starting a child process (org={}, project={}, repository={}, archive={}, sync={}, req={})",
                        org, project, in.repo(), archive, sync, req);
            } else {
                log.info("Starting a child process (org={}, project={}, repository={})", org, project, in.repo());
            }
        } else {
            if (debug) {
                log.info("Starting a new process (org={}, project={}, repository={}, archive={}, sync={}, req={}), on {}",
                        org, project, in.repo(), archive, sync, req, in.baseUrl());
            } else {
                log.info("Starting a new process (org={}, project={}, repository={}), on {}",
                        org, project, in.repo(), in.baseUrl());
            }
        }

        StartProcessResponse resp = withClient(in.baseUrl(), in.apiKey(),
                client -> new ProcessApi(client).startProcess(input));

        UUID processId = resp.getInstanceId();

        log.info("Started a process: {}",
                in.action() == Action.STARTEXTERNAL ? processId : LogTags.instanceId(processId));

        if (sync) {
            if (in.suspend()) {
                log.info("Suspending the process until the child process ({}) is completed...",
                        in.action() == Action.STARTEXTERNAL ? processId : LogTags.instanceId(processId));

                ResumePayload resume = new ResumePayload(
                        in.baseUrl(), in.apiKey(), !in.outVars().isEmpty(),
                        Collections.singletonList(processId), in.ignoreFailures());

                return suspend(resume, true);
            }

            Map<String, ProcessEntry> result = waitForCompletion(Collections.singletonList(processId), -1, Function.identity());
            handleResults(result, in.ignoreFailures());

            Map<String, Object> out = Collections.emptyMap();
            if (!in.outVars().isEmpty()) {
                out = getOutVars(in.baseUrl(), in.apiKey(), processId);
            }

            return TaskResult.success()
                    .value("id", processId.toString())
                    .values(out);
        }

        return TaskResult.success()
                .value("id", processId.toString());
    }

    public TaskResult continueAfterSuspend(ResumePayload payload) throws Exception {
        List<Result> results = new ArrayList<>();
        for (UUID processId : payload.jobs()) {
            Result r = continueAfterSuspend(payload.baseUrl(), payload.apiKey(), processId, payload.collectOutVars());
            results.add(r);
        }

        Map<String, ProcessEntry> instances = new HashMap<>();
        for (Result r : results) {
            UUID id = r.processEntry.getInstanceId();
            instances.put(id.toString(), r.processEntry);
        }

        handleResults(instances, payload.ignoreFailures());

        HashMap<String, Object> vars = new HashMap<>();
        for (Result r : results) {
            String id = r.processEntry.getInstanceId().toString();
            if (r.out != null) {
                vars.put(id, r.out);
            }
        }

        TaskResult.SimpleResult result = TaskResult.success()
                .value("ids", payload.jobs().stream().map(UUID::toString).collect(Collectors.toList()))
                .values(vars);

        boolean single = payload.jobs().size() == 1;
        if (single) {
            // for single job also put all variables at the top level of the result
            String id = payload.jobs().get(0).toString();
            Map<String, Object> out = results.get(0).out;
            result.value("id", id)
                    .values(out);
        }

        return result;
    }

    private Result continueAfterSuspend(String baseUrl, String apiKey, UUID processId, boolean collectOutVars) throws Exception {
        ProcessEntry e = ClientUtils.withRetry(3, 1000,
                () -> withClient(baseUrl, apiKey, client -> {
                    ProcessV2Api api = new ProcessV2Api(client);
                    return api.getProcess(processId, Collections.emptySet());
                }));

        ProcessEntry.StatusEnum s = e.getStatus();
        if (!isFinalStatus(s)) {
            throw new IllegalStateException("Process '" + processId + "' not finished");
        }

        if (collectOutVars) {
            return new Result(e, getOutVars(baseUrl, apiKey, processId));
        }

        return new Result(e, Collections.emptyMap());
    }

    private static class Result {

        private final ProcessEntry processEntry;
        private final Map<String, Object> out;

        private Result(ProcessEntry processEntry, Map<String, Object> out) {
            this.processEntry = processEntry;
            this.out = out;
        }
    }

    private TaskResult suspend(ResumePayload payload, boolean resumeFromSameStep) throws ApiException {
        if (payload.jobs().isEmpty()) {
            throw new RuntimeException("Jobs is empty");
        }

        String eventName = UUID.randomUUID().toString();

        Map<String, Object> condition = new HashMap<>();
        condition.put("type", "PROCESS_COMPLETION");
        condition.put("reason", "Waiting for a child process to end");
        condition.put("processes", payload.jobs());
        condition.put("resumeEvent", eventName);

        ClientUtils.withRetry(3, 1000, () -> withClient(client -> {
            ProcessApi api = new ProcessApi(client);
            api.setWaitCondition(currentProcessId, condition);
            return null;
        }));

        if (resumeFromSameStep) {
            return TaskResult.reentrantSuspend(eventName, payload.asMap());
        } else {
            return TaskResult.suspend(eventName);
        }
    }

    private static void handleResults(Map<String, ProcessEntry> m, boolean ignoreFailures) {
        StringBuilder errors = new StringBuilder();
        boolean hasErrors = false;
        for (Map.Entry<String, ProcessEntry> e : m.entrySet()) {
            String id = e.getKey();
            String status = e.getValue().getStatus().getValue();
            if (FAILED_STATUSES.contains(status)) {
                Map<String, Object> error = getError(e.getValue());
                String errorMessage = "";
                if (!error.isEmpty()) {
                    errorMessage = "(error: " + error + ")";
                }

                if (ignoreFailures) {
                    log.warn("Child process {} {} {}, ignoring...", id, status, errorMessage);
                    continue;
                }

                errors.append("Child process ").append(id).append(" ").append(status).append(" ").append(errorMessage);
                errors.append("\n");
                hasErrors = true;
            }
        }

        if (hasErrors) {
            throw new UserDefinedException(errors.toString());
        }
    }

    private static Map<String, Object> getError(ProcessEntry p) {
        Map<String, Object> meta = p.getMeta();
        if (meta == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> out = MapUtils.getMap(meta, "out", Collections.emptyMap());
        return MapUtils.getMap(out, Constants.Context.LAST_ERROR_KEY, Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOutVars(String baseUrl, String apiKey, UUID processId) throws Exception {
        return withClient(baseUrl, apiKey, client -> {
            ProcessApi api = new ProcessApi(client);

            try (InputStream is = api.downloadAttachment(processId, "out.json")) {
                ObjectMapper om = new ObjectMapper();
                return om.readValue(is, Map.class);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    return Collections.emptyMap();
                }
                log.error("Error while reading the out variables", e);
                throw e;
            }
        });
    }

    private TaskResult fork(ForkParams in) throws Exception {
        List<Future<UUID>> futures = new ArrayList<>();
        for (ForkStartParams fork : in.forks()) {
            int n = fork.getInstances();
            for (int i = 0; i < n; i++) {
                futures.add(forkOne(fork));
            }
        }

        // collect all futures, effectively blocking until all forks are started
        List<UUID> ids = new ArrayList<>();
        for (Future<UUID> f : futures) {
            ids.add(f.get());
        }

        boolean sync = in.sync();
        if (sync) {
            boolean suspend = in.suspend();
            if (suspend) {
                log.info("Suspending the process until the fork processes ({}) are completed...",
                        ids.stream().map(LogTags::instanceId).collect(Collectors.toList()));

                ResumePayload resume = new ResumePayload(
                        null, null, !in.outVars().isEmpty(), ids, in.ignoreFailures());

                return suspend(resume, true);
            }

            Map<String, ProcessEntry> result = waitForCompletion(ids, -1, Function.identity());
            handleResults(result, in.ignoreFailures());
        }

        TaskResult.SimpleResult result = TaskResult.success()
                .value("ids", ids.stream().map(UUID::toString).collect(Collectors.toList()));

        boolean single = ids.size() == 1;
        if (single) {
            result.value("id", ids.get(0).toString());
        }
        return result;
    }

    private Future<UUID> forkOne(ForkStartParams in) {
        if (in.payload() != null) {
            log.warn("'" + StartParams.PAYLOAD_KEY + "' parameter is not supported for fork action and will be ignored");
        }

        Map<String, Object> req = createRequest(in);

        if (in.debug(globalDebug)) {
            log.info("Forking the current instance (sync={}, req={})...", in.sync(), req);
        }

        return executor.submit(() -> withClient(in.apiKey(), client -> {
            ProcessApi api = new ProcessApi(client);
            StartProcessResponse resp = api.fork(currentProcessId, false, null, req);
            log.info("Forked a child process: {}", LogTags.instanceId(resp.getInstanceId()));
            return resp.getInstanceId();
        }));
    }

    private static void addIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v == null) {
            return;
        }
        m.put(k, v);
    }

    /**
     * Processes a list of attachment parameters. An attachment may be a string which represents
     * the path to a file or a Map which specifies the destination filename (key) and current filename (value)
     *
     * @param attachments List of attachments
     * @return Map of attachments. Key is the destination filename. Value is path to the local file.
     */
    @SuppressWarnings("rawtypes")
    private static Map<String, Path> processAttachments(Collection<Object> attachments) {
        if (attachments.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Path> result = new HashMap<>(attachments.size());
        for (Object o : attachments) {
            if (o instanceof String) {
                String key = ((String) o).replaceFirst("^.*/", "");

                result.put(key, Paths.get((String) o));
            } else if (o instanceof Map) {
                Map m = (Map) o;
                result.put((String) m.get("dest"), Paths.get((String) m.get("src")));
            } else {
                throw new IllegalArgumentException("Unsupported attachment formatting provided. Must be a string or map. Received " + o.getClass());
            }
        }

        return result;
    }

    private static Path archivePayload(Path workDir, StartParams in) throws IOException {
        String s = in.payload();
        if (s == null) {
            return null;
        }

        Path path = workDir.resolve(s);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File or directory not found: " + path);
        }

        if (Files.isDirectory(path)) {
            Path tmp = PathUtils.createTempFile("payload", ".zip");
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(tmp))) {
                IOUtils.zip(out, path);
            }
            return tmp;
        }

        return path;
    }

    private Map<String, Object> createRequest(StartParams in) {
        Map<String, Object> req = new HashMap<>();

        Set<String> activeProfiles = in.activeProfiles();
        if (activeProfiles != null) {
            req.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles);
        }

        String entryPoint = in.entryPoint();
        if (entryPoint != null) {
            req.put(Constants.Request.ENTRY_POINT_KEY, entryPoint);
        }

        Map<String, Object> exclusive = in.exclusive();
        if (!exclusive.isEmpty()) {
            req.put(Constants.Request.EXCLUSIVE, exclusive);
        }

        Set<String> tags = in.tags();
        if (tags != null) {
            req.put(Constants.Request.TAGS_KEY, tags);
        }

        Map<String, Object> args = in.arguments();
        if (!args.isEmpty()) {
            req.put(Constants.Request.ARGUMENTS_KEY, new HashMap<>(args));
        }

        boolean disableOnCancel = in.disableOnCancel();
        if (disableOnCancel) {
            req.put(Constants.Request.DISABLE_ON_CANCEL_KEY, true);
        }

        boolean disableOnFailure = in.disableOnFailure();
        if (disableOnFailure) {
            req.put(Constants.Request.DISABLE_ON_FAILURE_KEY, true);
        }

        Collection<String> outVars = in.outVars();
        if (!outVars.isEmpty()) {
            req.put(Constants.Request.OUT_EXPRESSIONS_KEY, outVars);
        }

        Object meta = in.meta();
        if (meta != null) {
            req.put(Constants.Request.META, meta);
        }

        Map<String, Object> requirements = in.requirements();
        if (!requirements.isEmpty()) {
            req.put(Constants.Request.REQUIREMENTS, new HashMap<>(requirements));
        }

        if (in.dryRunMode(dryRun)) {
            req.put(Constants.Request.DRY_RUN_MODE_KEY, true);
        }

        return req;
    }

    private String getOrg(StartParams in) {
        String org = in.org();
        if (org != null) {
            return org;
        }

        return currentOrgName;
    }

    private static boolean isFinalStatus(ProcessEntry.StatusEnum s) {
        return s == ProcessEntry.StatusEnum.FAILED
                || s == ProcessEntry.StatusEnum.FINISHED
                || s == ProcessEntry.StatusEnum.CANCELLED
                || s == ProcessEntry.StatusEnum.TIMED_OUT;
    }

    private <T> T withClient(CheckedFunction<ApiClient, T> f) throws Exception {
        return withClient(ApiClientConfiguration.builder().build(), f);
    }

    private <T> T withClient(String apiKey, CheckedFunction<ApiClient, T> f) throws Exception {
        return withClient(ApiClientConfiguration.builder()
                .apiKey(apiKey)
                .build(), f);
    }

    private <T> T withClient(String baseUrl, String apiKey, CheckedFunction<ApiClient, T> f) throws Exception {
        return withClient(ApiClientConfiguration.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build(), f);
    }

    private <T> T withClient(ApiClientConfiguration cfg, CheckedFunction<ApiClient, T> f) throws Exception {
        return f.apply(apiClientFactory.create(ApiClientConfiguration.builder().from(cfg).sessionToken(sessionToken).build()));
    }

    @FunctionalInterface
    interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
