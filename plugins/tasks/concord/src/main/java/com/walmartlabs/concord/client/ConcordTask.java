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
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.client.Keys.ACTION_KEY;
import static com.walmartlabs.concord.sdk.MapUtils.*;

@Named("concord")
@SuppressWarnings("unused")
public class ConcordTask extends AbstractConcordTask {

    private static final Logger log = LoggerFactory.getLogger(ConcordTask.class);

    private static final long DEFAULT_KILL_TIMEOUT = 10000;
    private static final long DEFAULT_POLL_DELAY = 5000;

    /**
     * @deprecated use {@link #PAYLOAD_KEY}
     */
    @Deprecated
    private static final String ARCHIVE_KEY = "archive";

    /**
     * @deprecated use {@link #REPO_KEY}
     */
    @Deprecated
    private static final String REPOSITORY_KEY = "repository";

    private static final String ACTIVE_PROFILES_KEY = "activeProfiles";
    private static final String ARGUMENTS_KEY = "arguments";
    private static final String BASE_URL_KEY = "baseUrl";
    private static final String DEBUG_KEY = "debug";
    private static final String DISABLE_ON_CANCEL_KEY = "disableOnCancel";
    private static final String DISABLE_ON_FAILURE_KEY = "disableOnFailure";
    private static final String ENTRY_POINT_KEY = "entryPoint";
    private static final String EXCLUSIVE_KEY = "exclusive";
    private static final String FORKS_KEY = "forks";
    private static final String IGNORE_FAILURES_KEY = "ignoreFailures";
    private static final String INSTANCE_ID_KEY = "instanceId";
    private static final String INSTANCES_KEY = "instances";
    private static final String JOB_OUT_KEY = "jobOut";
    private static final String JOBS_KEY = "jobs";
    private static final String ORG_KEY = "org";
    private static final String OUT_VARS_KEY = "outVars";
    private static final String PAYLOAD_KEY = "payload";
    private static final String ATTACHMENTS_KEY = "attachments";
    private static final String PROJECT_KEY = "project";
    private static final String REPO_BRANCH_OR_TAG_KEY = "repoBranchOrTag";
    private static final String REPO_COMMIT_ID_KEY = "repoCommitId";
    private static final String REPO_KEY = "repo";
    private static final String START_AT_KEY = "startAt";
    private static final String SYNC_KEY = "sync";
    private static final String TAGS_KEY = "tags";
    private static final String SUSPEND_KEY = "suspend";
    private static final String REQUIREMENTS_KEY = "requirements";
    private static final String META_KEY = "meta";

    private static final String SUSPEND_MARKER = "__concordTaskSuspend";
    private static final String RESUME_EVENT_NAME = "concordTask";

    private static final int MAX_EXECUTOR_THREADS = 20;

    private static final Set<String> FAILED_STATUSES;

    static {
        FAILED_STATUSES = new HashSet<>();
        FAILED_STATUSES.add(ProcessEntry.StatusEnum.FAILED.toString());
        FAILED_STATUSES.add(ProcessEntry.StatusEnum.CANCELLED.toString());
        FAILED_STATUSES.add(ProcessEntry.StatusEnum.TIMED_OUT.toString());
    }

    private final ExecutorService executor = new ThreadPoolExecutor(1, MAX_EXECUTOR_THREADS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    @InjectVariable("uiLinks")
    Map<String, Object> uiLinks;

    @InjectVariable("concord")
    Map<String, Object> defaults;

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);
        switch (action) {
            case START: {
                if (ContextUtils.getBoolean(ctx, SUSPEND_MARKER, false)) {
                    continueAfterSuspend(ctx);
                } else {
                    startChildProcess(ctx);
                }
                break;
            }
            case STARTEXTERNAL: {
                startExternalProcess(ctx);
                break;
            }
            case FORK: {
                if (ContextUtils.getBoolean(ctx, SUSPEND_MARKER, false)) {
                    continueAfterSuspend(ctx);
                } else {
                    fork(ctx);
                }
                break;
            }
            case KILL: {
                kill(ctx);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    public List<String> listSubprocesses(@InjectVariable("context") Context ctx, String instanceId, String... tags) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put(INSTANCE_ID_KEY, instanceId);
        if (tags != null) {
            m.put(TAGS_KEY, new HashSet<>(Arrays.asList(tags)));
        }
        return listSubprocesses(ctx, createJobCfg(ctx, m));
    }

    public List<String> listSubprocesses(@InjectVariable("context") Context ctx, Map<String, Object> cfg) throws Exception {
        UUID instanceId = getUUID(cfg, INSTANCE_ID_KEY);
        Set<String> tags = getSet(cfg, TAGS_KEY);

        return withClient(ctx, client -> {
            ProcessApi api = new ProcessApi(client);

            List<ProcessEntry> result = api.listSubprocesses(instanceId, tags);

            return result.stream()
                    .map(ProcessEntry::getInstanceId)
                    .map(UUID::toString)
                    .collect(Collectors.toList());
        });
    }

    public void suspendForCompletion(@InjectVariable("context") Context ctx, List<String> ids) throws Exception {
        suspend(ctx, ids, false);
    }

    public Map<String, ProcessEntry> waitForCompletion(@InjectVariable("context") Context ctx, List<String> ids) throws Exception {
        return waitForCompletion(ctx, ids, -1);
    }

    public Map<String, ProcessEntry> waitForCompletion(@InjectVariable("context") Context ctx, List<String> ids, long timeout) {
        return waitForCompletion(ctx, ids, timeout, p -> p);
    }

    public <T> Map<String, T> waitForCompletion(@InjectVariable("context") Context ctx, List<String> ids, long timeout, Function<ProcessEntry, T> processor) {
        Map<String, T> result = new ConcurrentHashMap<>();

        ids.parallelStream().map(UUID::fromString).forEach(id -> {
            log.info("Waiting for {}, URL: {}", id, getProcessUrl(ctx, id));

            long t1 = System.currentTimeMillis();
            while (true) {
                try {
                    ProcessEntry e = ClientUtils.withRetry(3, 1000, () -> withClient(ctx, client -> {
                        ProcessV2Api api = new ProcessV2Api(client);
                        return api.getProcess(id, Collections.singleton("childrenIds"));
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

    @SuppressWarnings("rawtypes")
    public void kill(@InjectVariable("context") Context ctx, Map<String, Object> cfg) throws Exception {
        List<String> ids = new ArrayList<>();

        Object v = cfg.get(INSTANCE_ID_KEY);
        if (v instanceof String || v instanceof UUID) {
            ids.add(v.toString());
        } else if (v instanceof String[] || v instanceof UUID[]) {
            Object[] os = (Object[]) v;
            for (Object o : os) {
                ids.add(o.toString());
            }
        } else if (v instanceof Collection) {
            for (Object o : (Collection) v) {
                if (o instanceof String || o instanceof UUID) {
                    ids.add(o.toString());
                } else {
                    throw new IllegalArgumentException("'" + INSTANCE_ID_KEY + "' value should be a string or an UUID: " + o);
                }
            }
        } else {
            throw new IllegalArgumentException("'" + INSTANCE_ID_KEY + "' should be a single string, an UUID value or an array of strings or UUIDs: " + v);
        }

        killMany(ctx, cfg, ids);
    }

    public Map<String, Map<String, Object>> getOutVars(@InjectVariable("context") Context ctx, List<String> ids) {
        return getOutVars(ctx, ids, -1);
    }

    public Map<String, Map<String, Object>> getOutVars(@InjectVariable("context") Context ctx, List<String> ids, long timeout) {
        return waitForCompletion(ctx, ids, timeout, p -> {
            try {
                return getOutVars(ctx, p.getInstanceId());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void startExternalProcess(Context ctx) throws Exception {
        // just the validation. AbstractConcordTask#withClient will take care of passing the API key into the client
        ContextUtils.assertString("'" + API_KEY + "' is required to start a process on an external Concord instance",
                ctx, API_KEY);

        Map<String, Object> cfg = createJobCfg(ctx, defaults);
        boolean sync = getBoolean(cfg, SYNC_KEY, false);
        boolean suspend = getBoolean(cfg, SUSPEND_KEY, false);
        if (sync && suspend) {
            log.warn("Input parameter '{}' ignored for {} action", SUSPEND_KEY, Action.STARTEXTERNAL);
            cfg.put(SUSPEND_KEY, false);
        }

        start(ctx, cfg, null);
    }

    private void startChildProcess(Context ctx) throws Exception {
        UUID parentInstanceId = ContextUtils.getTxId(ctx);
        start(ctx, parentInstanceId);
    }

    private void start(Context ctx, UUID parentInstanceId) throws Exception {
        Map<String, Object> cfg = createJobCfg(ctx, defaults);
        start(ctx, cfg, parentInstanceId);
    }

    private void start(Context ctx, Map<String, Object> cfg, UUID parentInstanceId) throws Exception {
        Map<String, Object> req = createRequest(cfg);

        Path workDir = ContextUtils.getWorkDir(ctx);

        Path archive = archivePayload(workDir, cfg);
        String project = getString(cfg, PROJECT_KEY);
        if (project == null && archive == null) {
            throw new IllegalArgumentException("'" + PAYLOAD_KEY + "' and/or '" + PROJECT_KEY + "' are required");
        }

        Map<String, Object> input = new HashMap<>();

        if (archive != null) {
            input.put("archive", Files.readAllBytes(archive));
        }

        ObjectMapper om = new ObjectMapper();
        input.put("request", om.writeValueAsBytes(req));

        String org = getOrg(ctx, cfg);
        addIfNotNull(input, "org", org);
        addIfNotNull(input, "project", project);

        String repo = getRepo(cfg);
        addIfNotNull(input, "repo", repo);

        List<Object> attachments = getList(cfg, ATTACHMENTS_KEY, Collections.emptyList());
        processAttachments(attachments).forEach((d, p) -> addIfNotNull(input, d, p));

        String repoBranchOrTag = getString(cfg, REPO_BRANCH_OR_TAG_KEY);
        addIfNotNull(input, "repoBranchOrTag", repoBranchOrTag);

        String repoCommitId = getString(cfg, REPO_COMMIT_ID_KEY);
        addIfNotNull(input, "repoCommitId", repoCommitId);

        String startAt = getStartAt(cfg);
        addIfNotNull(input, "startAt", startAt);

        addIfNotNull(input, "parentInstanceId", parentInstanceId);

        boolean sync = getBoolean(cfg, SYNC_KEY, false);
        boolean debug = getBoolean(cfg, DEBUG_KEY, false);
        if (parentInstanceId != null) {
            if (debug) {
                log.info("Starting a child process (org={}, project={}, repository={}, archive={}, sync={}, req={})",
                        org, project, repo, archive, sync, req);
            } else {
                log.info("Starting a child process (org={}, project={}, repository={})", org, project, repo);
            }
        } else {
            if (debug) {
                log.info("Starting a new process (org={}, project={}, repository={}, archive={}, sync={}, req={}), on {}",
                        org, project, repo, archive, sync, req, ctx.getVariable(BASE_URL_KEY));
            } else {
                log.info("Starting a new process (org={}, project={}, repository={}), on {}",
                        org, project, repo, ctx.getVariable(BASE_URL_KEY));
            }
        }

        StartProcessResponse resp = withClient(ctx, apiClient -> new ProcessApi(apiClient).startProcess(input));

        UUID processId = resp.getInstanceId();

        log.info("Started a process: {}, URL: {}", processId, getProcessUrl(ctx, processId));

        List<String> jobs = Collections.singletonList(processId.toString());
        ctx.setVariable(JOBS_KEY, jobs);

        if (sync) {
            boolean suspend = getBoolean(cfg, SUSPEND_KEY, false);
            if (suspend) {
                log.info("Suspending the process until the child process ({}) is completed...", processId);
                suspend(ctx, jobs, true);
                return;
            }

            Map<String, ProcessEntry> result = waitForCompletion(ctx, jobs);
            handleResults(cfg, result);

            Object out = null;
            if (cfg.containsKey(OUT_VARS_KEY)) {
                out = getOutVars(ctx, processId);
            }
            ctx.setVariable(JOB_OUT_KEY, out != null ? out : Collections.emptyMap());
        }
    }

    @SuppressWarnings("unchecked")
    private void continueAfterSuspend(Context ctx) throws Exception {
        ctx.removeVariable(SUSPEND_MARKER);

        Map<String, Object> cfg = createJobCfg(ctx, defaults);

        List<String> jobs = (List<String>) ctx.getVariable(JOBS_KEY);

        List<Result> results = new ArrayList<>();
        for (String processId : jobs) {
            Result r = continueAfterSuspend(ctx, cfg, UUID.fromString(processId));
            results.add(r);
        }

        Map<String, ProcessEntry> instances = new HashMap<>();
        for (Result r : results) {
            String id = r.processEntry.getInstanceId().toString();
            instances.put(id, r.processEntry);
        }

        handleResults(cfg, instances);

        boolean single = jobs.size() == 1;

        if (single) {
            // if only one job was started put all variables at the top level of the jobOut object
            // e.g. jobOut.someVar
            Map<String, Object> out = results.get(0).out;
            ctx.setVariable(JOB_OUT_KEY, out != null ? out : Collections.emptyMap());
        } else {
            // for multiple jobs save their variable into a nested map
            // e.g. jobOut['PROCESSID'].someVar
            Map<String, Map<String, Object>> vars = new HashMap<>();
            for (Result r : results) {
                String id = r.processEntry.getInstanceId().toString();
                if (r.out != null) {
                    vars.put(id, r.out);
                }
            }
            ctx.setVariable(JOB_OUT_KEY, vars);
        }
    }

    private Result continueAfterSuspend(Context ctx, Map<String, Object> cfg, UUID processId) throws Exception {
        ProcessEntry e = ClientUtils.withRetry(3, 1000, () -> withClient(ctx, client -> {
            ProcessV2Api api = new ProcessV2Api(client);
            return api.getProcess(processId, Collections.singleton("childrenIds"));
        }));

        ProcessEntry.StatusEnum s = e.getStatus();
        if (!isFinalStatus(s)) {
            throw new IllegalStateException("Process '" + processId + "' not finished");
        }

        if (cfg.containsKey(OUT_VARS_KEY)) {
            return new Result(e, getOutVars(ctx, processId));
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

    private void suspend(Context ctx, List<String> jobs, boolean resumeFromSameStep) throws ApiException {
        if (jobs.isEmpty()) {
            return;
        }

        Map<String, Object> condition = new HashMap<>();
        condition.put("type", "PROCESS_COMPLETION");
        condition.put("reason", "Waiting for a child process to end");
        condition.put("processes", jobs);
        condition.put("resumeEvent", RESUME_EVENT_NAME);

        ClientUtils.withRetry(3, 1000, () -> withClient(ctx, false, client -> {
            ProcessApi api = new ProcessApi(client);
            api.setWaitCondition(ContextUtils.getTxId(ctx), condition);
            return null;
        }));

        if (resumeFromSameStep) {
            ctx.setVariable(SUSPEND_MARKER, true);
        }

        ctx.suspend(RESUME_EVENT_NAME, null, resumeFromSameStep);
    }

    private static void handleResults(Map<String, Object> cfg, Map<String, ProcessEntry> m) {
        StringBuilder errors = new StringBuilder();
        boolean hasErrors = false;
        boolean ignoreFailures = getBoolean(cfg, IGNORE_FAILURES_KEY, false);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getError(ProcessEntry p) {
        Map<String, Object> meta = p.getMeta();
        if (meta == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> out = (Map<String, Object>) meta.get("out");
        if (out == null) {
            return Collections.emptyMap();
        }

        return (Map<String, Object>) out.getOrDefault(Constants.Context.LAST_ERROR_KEY, Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOutVars(Context ctx, UUID processId) throws Exception {
        return withClient(ctx, client -> {
            ProcessApi api = new ProcessApi(client);

            try (InputStream is = api.downloadAttachment(processId, "out.json")){
                ObjectMapper om = new ObjectMapper();
                return om.readValue(is, Map.class);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    return null;
                }
                log.error("Error while reading the out variables", e);
                throw e;
            }
        });
    }

    private void fork(Context ctx) throws Exception {
        List<Map<String, Object>> jobs = ContextUtils.getList(ctx, FORKS_KEY, null);
        if (jobs == null) {
            jobs = Collections.singletonList(createJobCfg(ctx, null));
        }

        if (jobs.isEmpty()) {
            throw new IllegalArgumentException("'" + FORKS_KEY + "' can't be an empty list");
        }

        List<String> jobIds = forkMany(ctx, jobs);
        ctx.setVariable(JOBS_KEY, jobIds);
    }

    private List<String> forkMany(Context ctx, List<Map<String, Object>> jobs) throws Exception {
        List<String> ids = new ArrayList<>();

        List<Future<UUID>> futures = new ArrayList<>();
        for (Map<String, Object> job : jobs) {
            Map<String, Object> cfg = createJobCfg(ctx, job);
            cfg.put(INSTANCE_ID_KEY, ctx.getVariable(Constants.Context.TX_ID_KEY));

            int n = getInstances(cfg);
            for (int i = 0; i < n; i++) {
                futures.add(forkOne(ctx, cfg));
            }

        }

        // collect all futures, effectively blocking until all forks are started
        for (Future<UUID> f : futures) {
            ids.add(f.get().toString());
        }

        Map<String, Object> cfg = createJobCfg(ctx, defaults);
        boolean sync = getBoolean(cfg, SYNC_KEY, false);
        if (sync) {
            boolean suspend = getBoolean(cfg, SUSPEND_KEY, false);
            if (suspend) {
                log.info("Suspending the process until the fork processes ({}) are completed...", ids);
                suspend(ctx, ids, true);
                return ids;
            }

            Map<String, ProcessEntry> result = waitForCompletion(ctx, ids);
            handleResults(cfg, result);
        }

        return ids;
    }

    private Future<UUID> forkOne(Context ctx, Map<String, Object> cfg) {
        if (cfg.containsKey(ARCHIVE_KEY)) {
            log.warn("'" + ARCHIVE_KEY + "' parameter is not supported for fork action and will be ignored");
        }

        if (!cfg.containsKey(ENTRY_POINT_KEY)) {
            throw new IllegalArgumentException("'" + ENTRY_POINT_KEY + "' is required");
        }

        Map<String, Object> req = createRequest(cfg);

        UUID instanceId = assertUUID(cfg, INSTANCE_ID_KEY);

        boolean sync = getBoolean(cfg, SYNC_KEY, false);
        boolean debug = getBoolean(cfg, DEBUG_KEY, false);
        if (debug) {
            log.info("Forking the current instance (sync={}, req={})...", sync, req);
        }

        return executor.submit(() -> withClient(ctx, client -> {
            ProcessApi api = new ProcessApi(client);
            StartProcessResponse resp = api.fork(instanceId, false, null, req);
            log.info("Forked a child process: {} url: {}", resp.getInstanceId(), getProcessUrl(ctx, resp.getInstanceId()));
            return resp.getInstanceId();
        }));
    }

    private void kill(Context ctx) throws Exception {
        Map<String, Object> cfg = createCfg(ctx, INSTANCE_ID_KEY);
        kill(ctx, cfg);
    }

    private void killMany(Context ctx, Map<String, Object> cfg, List<String> instanceIds) throws Exception {
        if (instanceIds == null) {
            throw new IllegalArgumentException("'" + INSTANCE_ID_KEY + "' should be a single value or an array of values: " + instanceIds);
        }

        if (instanceIds.isEmpty()) {
            log.warn("kill: no process IDs specified, nothing to do.");
            return;
        }

        for (String id : instanceIds) {
            killOne(ctx, cfg, id);
        }
    }

    private void killOne(Context ctx, Map<String, Object> cfg, String instanceId) throws Exception {
        withClient(ctx, client -> {
            ProcessApi api = new ProcessApi(client);
            api.kill(UUID.fromString(instanceId));
            return null;
        });

        boolean sync = getBoolean(cfg, SYNC_KEY, false);
        if (sync) {
            waitForCompletion(ctx, Collections.singletonList(instanceId), DEFAULT_KILL_TIMEOUT);
        }
    }

    private Map<String, Object> createJobCfg(Context ctx, Map<String, Object> job) {
        Map<String, Object> m = createCfg(ctx,
                ACTIVE_PROFILES_KEY,
                ARCHIVE_KEY,
                ARGUMENTS_KEY,
                DEBUG_KEY,
                DISABLE_ON_CANCEL_KEY,
                DISABLE_ON_FAILURE_KEY,
                ENTRY_POINT_KEY,
                EXCLUSIVE_KEY,
                IGNORE_FAILURES_KEY,
                INSTANCE_ID_KEY,
                ORG_KEY,
                OUT_VARS_KEY,
                PAYLOAD_KEY,
                ATTACHMENTS_KEY,
                PROJECT_KEY,
                REPO_BRANCH_OR_TAG_KEY,
                REPO_COMMIT_ID_KEY,
                REPO_KEY,
                REPOSITORY_KEY,
                START_AT_KEY,
                SYNC_KEY,
                TAGS_KEY,
                SUSPEND_KEY,
                REQUIREMENTS_KEY,
                META_KEY);

        if (job != null) {
            m.putAll(job);
        }

        return m;
    }

    private String getProcessUrl(Context ctx, UUID processId) {
        String action = ContextUtils.getString(ctx, ACTION_KEY);
        if (Action.STARTEXTERNAL.name().equalsIgnoreCase(action) || uiLinks == null) {
            return "n/a";
        }

        String processLinkTemplate = getString(uiLinks, "process");
        if (processLinkTemplate == null) {
            return "n/a";
        }

        String baseUrl = getBaseUrl(ctx);
        if (action == null && baseUrl != null && !processLinkTemplate.startsWith(baseUrl)) {
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
    private static Map<String, Path> processAttachments(List<Object> attachments) {
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

    private static Path archivePayload(Path workDir, Map<String, Object> cfg) throws IOException {
        String s = getString(cfg, PAYLOAD_KEY);
        if (s == null) {
            s = getString(cfg, ARCHIVE_KEY);
            if (s != null) {
                log.warn("'{}' is deprecated, please use '{}' parameter", ARCHIVE_KEY, PAYLOAD_KEY);
            }
        }

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createRequest(Map<String, Object> cfg) {
        Map<String, Object> req = new HashMap<>();

        Set<String> activeProfiles = getSet(cfg, ACTIVE_PROFILES_KEY);
        if (activeProfiles != null) {
            req.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles);
        }

        String entryPoint = getString(cfg, ENTRY_POINT_KEY);
        if (entryPoint != null) {
            req.put(Constants.Request.ENTRY_POINT_KEY, entryPoint);
        }

        if (cfg.get(EXCLUSIVE_KEY) != null) {
            Map<String, Object> exclusive = MapUtils.getMap(cfg, EXCLUSIVE_KEY, null);
            req.put(Constants.Request.EXCLUSIVE, exclusive);
        }

        Set<String> tags = getSet(cfg, TAGS_KEY);
        if (tags != null) {
            req.put(Constants.Request.TAGS_KEY, tags);
        }

        Map<String, Object> args = getMap(cfg, ARGUMENTS_KEY, Collections.emptyMap());
        if (!args.isEmpty()) {
            req.put(Constants.Request.ARGUMENTS_KEY, new HashMap<>(args));
        }

        if (getBoolean(cfg, DISABLE_ON_CANCEL_KEY, false)) {
            req.put(Constants.Request.DISABLE_ON_CANCEL_KEY, true);
        }

        if (getBoolean(cfg, DISABLE_ON_FAILURE_KEY, false)) {
            req.put(Constants.Request.DISABLE_ON_FAILURE_KEY, true);
        }

        Collection<String> outVars = (Collection<String>) cfg.get(OUT_VARS_KEY);
        if (outVars != null && !outVars.isEmpty()) {
            req.put(Constants.Request.OUT_EXPRESSIONS_KEY, outVars);
        }

        Object meta = cfg.get(META_KEY);
        if (meta != null) {
            req.put(Constants.Request.META, meta);
        }

        Map<String, Object> requirements = getMap(cfg, Constants.Request.REQUIREMENTS, Collections.emptyMap());
        if (!requirements.isEmpty()) {
            req.put(Constants.Request.REQUIREMENTS, new HashMap<>(requirements));
        }

        return req;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getSet(Map<String, Object> cfg, String k) {
        if (cfg == null) {
            return null;
        }

        Object v = cfg.get(k);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return Arrays.stream(((String) v)
                            .split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
        } else if (v instanceof String[]) {
            return new HashSet<>(Arrays.asList((String[]) v));
        } else if (v instanceof Collection) {
            return new HashSet<>((Collection) v);
        } else {
            throw new IllegalArgumentException("'" + k + "' must a single string value or an array of strings: " + v);
        }
    }

    private static String getOrg(Context ctx, Map<String, Object> cfg) {
        String org = getString(cfg, ORG_KEY);
        if (org != null) {
            return org;
        }

        ProjectInfo pi = ContextUtils.getProjectInfo(ctx);
        if (pi != null) {
            return pi.orgName();
        }
        return null;
    }

    private static String getRepo(Map<String, Object> cfg) {
        String repo = getString(cfg, REPO_KEY);
        if (repo != null) {
            return repo;
        }
        repo = getString(cfg, REPOSITORY_KEY);
        if (repo != null) {
            log.warn("'{}' is deprecated, please use '{}' parameter", REPOSITORY_KEY, REPO_KEY);
        }
        return repo;
    }

    private static Action getAction(Context ctx) {
        String action = ContextUtils.assertString(ctx, ACTION_KEY);
        try {
            return Action.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown action: '" + action + "'. Available actions: " + Arrays.toString(Action.values()));
        }
    }

    private static int getInstances(Map<String, Object> cfg) {
        int i;

        Object v = cfg.getOrDefault(INSTANCES_KEY, 1);
        if (v instanceof Integer) {
            i = (Integer) v;
        } else if (v instanceof Long) {
            i = ((Long) v).intValue();
        } else {
            throw new IllegalArgumentException("'" + INSTANCES_KEY + "' must be a number");
        }

        if (i <= 0) {
            throw new IllegalArgumentException("'" + INSTANCES_KEY + "' must be a positive number");
        }

        return i;
    }

    private static String getStartAt(Map<String, Object> cfg) {
        Object v = cfg.get(START_AT_KEY);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return (String) v;
        } else if (v instanceof Date) {
            Calendar c = Calendar.getInstance();
            c.setTime((Date) v);
            return DatatypeConverter.printDateTime(c);
        } else if (v instanceof Calendar) {
            return DatatypeConverter.printDateTime((Calendar) v);
        } else {
            throw new IllegalArgumentException("'" + START_AT_KEY + "' must be a string, java.util.Date or java.util.Calendar value. Got: " + v);
        }
    }

    private static boolean isFinalStatus(ProcessEntry.StatusEnum s) {
        return s == ProcessEntry.StatusEnum.FAILED
                || s == ProcessEntry.StatusEnum.FINISHED
                || s == ProcessEntry.StatusEnum.CANCELLED
                || s == ProcessEntry.StatusEnum.TIMED_OUT;
    }


    private enum Action {

        START,
        STARTEXTERNAL,
        FORK,
        KILL
    }
}