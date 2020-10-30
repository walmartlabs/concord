package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.client.ConcordTaskParams.*;

@Named("concord")
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
    private final String processLinkTemplate;
    private final UUID currentProcessId;
    private final String currentOrgName;
    private final Path workDir;

    public ConcordTaskCommon(String sessionToken, ApiClientFactory apiClientFactory, String processLinkTemplate, UUID currentProcessId, String currentOrgName, Path workDir) {
        this.sessionToken = sessionToken;
        this.apiClientFactory = apiClientFactory;
        this.processLinkTemplate = processLinkTemplate;
        this.currentProcessId = currentProcessId;
        this.currentOrgName = currentOrgName;
        this.workDir = workDir;
    }

    public TaskResult execute(ConcordTaskParams in) throws Exception {
        Action action = in.action();
        switch (action) {
            case START: {
                return startChildProcess((StartParams) in);
            }
            case STARTEXTERNAL: {
                return startExternalProcess((StartExternalParams) in);
            }
            case FORK: {
                return fork((ForkParams) in);
            }
            case KILL: {
                kill((KillParams) in);
                return TaskResult.success();
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    public List<ProcessEntry> listSubProcesses(ListSubProcesses in) throws Exception {
        UUID instanceId = in.instanceId();
        Set<String> tags = in.tags();

        return withClient(client -> {
            ProcessApi api = new ProcessApi(client);

            List<String> tl = tags != null ? new ArrayList<>(tags) : null;
            return api.listSubprocesses(instanceId, tl);
        });
    }

    public void suspendForCompletion(List<UUID> ids) throws Exception {
        suspend(new ResumePayload(null, null, false, ids, false), false);
    }

    public <T> Map<String, T> waitForCompletion(List<UUID> ids, long timeout, Function<ProcessEntry, T> processor) {
        Map<String, T> result = new HashMap<>();

        ids.parallelStream().forEach(id -> {
            log.info("Waiting for {}, URL: {}", id, getProcessUrl(id));

            long t1 = System.currentTimeMillis();
            while (true) {
                try {
                    ProcessEntry e = ClientUtils.withRetry(3, 1000,
                            () -> withClient(client -> {
                                ProcessApi api = new ProcessApi(client);
                                return api.get(id);
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
        for (UUID id : in.ids()) {
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
        // just the validation
        in.apiKey();

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
            input.put("archive", Files.readAllBytes(archive));
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
        boolean debug = in.debug();
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
                client -> RequestUtils.request(client, "/api/v1/process", "POST", input, StartProcessResponse.class));

        UUID processId = resp.getInstanceId();

        log.info("Started a process: {}, URL: {}", processId, getProcessUrl(in, processId));

        if (sync) {
            if (in.suspend()) {
                log.info("Suspending the process until the child process ({}) is completed...", processId);
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
                    .values(out);
        }

        return TaskResult.success();
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

        boolean single = payload.jobs().size() == 1;

        if (single) {
            // if only one job was started put all variables at the top level of the jobOut object
            // e.g. jobOut.someVar
            Map<String, Object> out = results.get(0).out;
            return TaskResult.success()
                    .values(out);
        } else {
            // for multiple jobs save their variable into a nested map
            // e.g. jobOut['PROCESSID'].someVar
            HashMap<String, Object> vars = new HashMap<>();
            for (Result r : results) {
                String id = r.processEntry.getInstanceId().toString();
                if (r.out != null) {
                    vars.put(id, r.out);
                }
            }
            return TaskResult.success()
                    .values(vars);
        }
    }

    private Result continueAfterSuspend(String baseUrl, String apiKey, UUID processId, boolean collectOutVars) throws Exception {
        ProcessEntry e = ClientUtils.withRetry(3, 1000,
                () -> withClient(baseUrl, apiKey, client -> {
                    ProcessApi api = new ProcessApi(client);
                    return api.get(processId);
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
            throw new IllegalStateException(errors.toString());
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

            File f = null;
            try {
                f = api.downloadAttachment(processId, "out.json");
                ObjectMapper om = new ObjectMapper();
                return om.readValue(f, Map.class);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    return Collections.emptyMap();
                }
                log.error("Error while reading the out variables", e);
                throw e;
            } finally {
                IOUtils.delete(f);
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
                log.info("Suspending the process until the fork processes ({}) are completed...", ids);
                ResumePayload resume = new ResumePayload(
                        null, null, !in.outVars().isEmpty(), ids, in.ignoreFailures());

                return suspend(resume, true);
            }

            Map<String, ProcessEntry> result = waitForCompletion(ids, -1, Function.identity());
            handleResults(result, in.ignoreFailures());
        }

        return TaskResult.success()
                .value("forks", ids.stream()
                        .map(UUID::toString)
                        .collect(Collectors.toList()));
    }

    private Future<UUID> forkOne(ForkStartParams in) {
        if (in.payload() != null) {
            log.warn("'" + StartParams.PAYLOAD_KEY + "' parameter is not supported for fork action and will be ignored");
        }

        Map<String, Object> req = createRequest(in);

        if (in.debug()) {
            log.info("Forking the current instance (sync={}, req={})...", in.sync(), req);
        }

        return executor.submit(() -> withClient(in.apiKey(), client -> {
            ProcessApi api = new ProcessApi(client);
            StartProcessResponse resp = api.fork(currentProcessId, req, false, null);
            log.info("Forked a child process: {} url: {}", resp.getInstanceId(), getProcessUrl(in, resp.getInstanceId()));
            return resp.getInstanceId();
        }));
    }

    private String getProcessUrl(ConcordTaskParams in, UUID processId) {
        Action action = in.action();
        if (action == Action.STARTEXTERNAL || processLinkTemplate == null) {
            return "n/a";
        }

        return String.format(processLinkTemplate, processId);
    }

    private String getProcessUrl(UUID processId) {
        if (processLinkTemplate == null) {
            return "n/a";
        }

        return String.format(processLinkTemplate, processId);
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
            Path tmp = IOUtils.createTempFile("payload", ".zip");
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(tmp))) {
                IOUtils.zip(out, path);
            }
            return tmp;
        }

        return path;
    }

    private static Map<String, Object> createRequest(StartParams in) {
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
