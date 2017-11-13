package com.walmartlabs.concord.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.client.Keys.BASEURL_KEY;
import static com.walmartlabs.concord.client.Keys.SESSION_TOKEN_KEY;

@Named("concord")
public class ConcordTask extends AbstractConcordTask {

    private static final Logger log = LoggerFactory.getLogger(ConcordTask.class);

    private static long DEFAULT_KILL_TIMEOUT = 10000;

    private static final String ACTION_KEY = "action";
    private static final String ARCHIVE_KEY = "archive";
    private static final String PROJECT_KEY = "project";
    private static final String REPOSITORY_KEY = "repository";
    private static final String SYNC_KEY = "sync";
    private static final String ENTRY_POINT_KEY = "entryPoint";
    private static final String ARGUMENTS_KEY = "arguments";
    private static final String OUT_VARS_KEY = "outVars";
    private static final String JOBS_KEY = "jobs";
    private static final String INSTANCES_KEY = "instances";
    private static final String INSTANCE_ID_KEY = "instanceId";
    private static final String TAGS_KEY = "tags";
    private static final String DISABLE_ON_CANCEL_KEY = "disableOnCancel";
    private static final String DISABLE_ON_FAILURE_KEY = "disableOnFailure";
    private static final String JOB_OUT_KEY = "jobOut";

    @InjectVariable("concord")
    Map<String, Object> defaults;

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);
        switch (action) {
            case START: {
                String instanceId = (String) ctx.getVariable(Constants.Context.TX_ID_KEY);
                start(ctx, instanceId);
                break;
            }
            case FORK: {
                fork(ctx);
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

    @SuppressWarnings("unchecked")
    public List<String> listSubprocesses(@InjectVariable("context") Context ctx, Map<String, Object> cfg) throws Exception {
        String instanceId = get(cfg, INSTANCE_ID_KEY);

        String target = get(cfg, BASEURL_KEY) + "/api/v1/process/" + instanceId + "/subprocess";
        String apiKey = get(cfg, SESSION_TOKEN_KEY);

        Set<String> tags = getTags(cfg);
        if (tags != null) {
            StringBuilder b = new StringBuilder("?");
            for (Iterator<String> i = tags.iterator(); i.hasNext(); ) {
                b.append("tags=").append(i.next());
                if (i.hasNext()) {
                    b.append("&");
                }
            }
            target += b;
        }

        URL url = new URL(target);
        List<Map<String, Object>> l = Http.getJson(url, apiKey, List.class);

        return l.stream().map(e -> (String) e.get("instanceId")).collect(Collectors.toList());
    }

    public Map<String, Object> waitForCompletion(@InjectVariable("context") Context ctx, List<String> ids) throws Exception {
        return waitForCompletion(ctx, defaults, ids, -1);
    }

    public Map<String, Object> waitForCompletion(@InjectVariable("context") Context ctx, List<String> ids, long timeout) throws Exception {
        return waitForCompletion(ctx, defaults, ids, timeout);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> waitForCompletion(@InjectVariable("context") Context ctx, Map<String, Object> cfg, List<String> ids, long timeout) throws Exception {
        String apiKey = get(cfg, SESSION_TOKEN_KEY);

        Map<String, Object> result = new HashMap<>();

        ids.parallelStream().forEach(id -> {
            log.info("Waiting for {}...", id);
            try {
                String target = get(cfg, BASEURL_KEY) + "/api/v1/process/" + id + "/waitForCompletion";
                URL url = new URL(target + "?timeout=" + timeout);
                Map<String, Object> m = Http.getJson(url, apiKey, Map.class);
                String status = (String) m.get("status");
                log.info("Process {} is {}", id, status);

                m.put(id, status);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return result;
    }

    private void start(Context ctx, String instanceId) throws Exception {
        Map<String, Object> cfg = createJobCfg(ctx, defaults);

        String project = (String) cfg.get(PROJECT_KEY);
        String repo = (String) cfg.get(REPOSITORY_KEY);
        Path archive = null;

        Map<String, Object> req = createRequest(cfg);
        boolean sync = (boolean) cfg.getOrDefault(SYNC_KEY, false);

        if (cfg.containsKey(ARCHIVE_KEY)) {
            Path workDir = Paths.get((String) ctx.getVariable(Constants.Context.WORK_DIR_KEY));
            archive = workDir.resolve((String) cfg.get(ARCHIVE_KEY));
            if (!Files.exists(archive) || !Files.isRegularFile(archive)) {
                throw new IllegalArgumentException("File not found: " + archive);
            }
        } else if (project == null) {
            throw new IllegalArgumentException("'" + ARCHIVE_KEY + "' and/or '" + PROJECT_KEY + "' are required");
        }

        log.info("Starting a child process (project={}, repository={}, archive={} sync={}, req={})",
                project, repo, archive, sync, req);

        String target = get(cfg, BASEURL_KEY) + "/api/v1/process";
        if (project != null) {
            target += "/" + project;
            if (repo != null) {
                target += ":" + repo;
            }
        }

        String apiKey = get(cfg, SESSION_TOKEN_KEY);

        URL url = new URL(target + "?parentId=" + instanceId + "&sync=" + sync);
        HttpURLConnection conn = null;
        try {
            Map<String, Object> input = new HashMap<>();

            if (archive != null) {
                input.put("archive", Files.readAllBytes(archive));
            }

            ObjectMapper om = new ObjectMapper();
            input.put("request", om.writeValueAsBytes(req));

            conn = Http.postMultipart(url, apiKey, input);

            Map<String, Object> result = Http.readMap(conn);
            String childId = (String) result.get("instanceId");
            log.info(sync ? "Child process completed: {}" : "Started a child process: {}", childId);

            ctx.setVariable(JOBS_KEY, Collections.singletonList(childId));
            Object out = result.getOrDefault("out", Collections.emptyMap());
            ctx.setVariable(JOB_OUT_KEY, out);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void fork(Context ctx) throws Exception {
        List<Map<String, Object>> jobs;

        Object v = ctx.getVariable(JOBS_KEY);
        if (v != null) {
            if (v instanceof List) {
                jobs = (List<Map<String, Object>>) v;
            } else {
                throw new IllegalArgumentException("'" + JOBS_KEY + "' must be a list");
            }
        } else {
            jobs = Collections.singletonList(createJobCfg(ctx, (Map<String, Object>) null));
        }

        List<String> jobIds = forkMany(ctx, jobs);
        ctx.setVariable(JOBS_KEY, jobIds);
    }

    private List<String> forkMany(Context ctx, List<Map<String, Object>> jobs) throws Exception {
        if (jobs.isEmpty()) {
            throw new IllegalArgumentException("'" + JOBS_KEY + "' can't be an empty list");
        }

        List<String> ids = new ArrayList<>();

        for (Map<String, Object> job : jobs) {
            Map<String, Object> cfg = createJobCfg(ctx, job);
            cfg.put(INSTANCE_ID_KEY, ctx.getVariable(Constants.Context.TX_ID_KEY));

            int n = getInstances(cfg);
            for (int i = 0; i < n; i++) {
                String id = forkOne(cfg);
                ids.add(id);
            }
        }

        return ids;
    }

    private String forkOne(Map<String, Object> cfg) throws Exception {
        if (cfg.containsKey(ARCHIVE_KEY)) {
            log.warn("'" + ARCHIVE_KEY + "' parameter is not supported for fork action and will be ignored");
        }

        String instanceId = get(cfg, INSTANCE_ID_KEY);
        String target = get(cfg, BASEURL_KEY) + "/api/v1/process/" + instanceId + "/fork";
        String apiKey = get(cfg, SESSION_TOKEN_KEY);
        boolean sync = (boolean) cfg.getOrDefault(SYNC_KEY, false);

        Map<String, Object> req = createRequest(cfg);

        log.info("Forking the current instance (sync={}, req={})...", sync, req);
        URL url = new URL(target + "?sync=" + sync);
        HttpURLConnection conn = null;
        try {
            conn = Http.postJson(url, apiKey, req);

            Map<String, Object> result = Http.readMap(conn);

            String childId = (String) result.get("instanceId");
            log.info("Forked a child process: {}", childId);
            return childId;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void kill(Context ctx) throws Exception {
        Map<String, Object> cfg = createCfg(ctx);
        kill(ctx, cfg);
    }

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

    private void killMany(Context ctx, Map<String, Object> cfg, List<String> instanceIds) throws Exception {
        if (instanceIds == null || instanceIds.isEmpty()) {
            throw new IllegalArgumentException("'" + INSTANCE_ID_KEY + "' should be a single value or an array of values: " + instanceIds);
        }

        for (String id : instanceIds) {
            killOne(ctx, cfg, id);
        }
    }

    private void killOne(Context ctx, Map<String, Object> cfg, String instanceId) throws Exception {
        String target = get(cfg, BASEURL_KEY) + "/api/v1/process/" + instanceId;
        String apiKey = get(cfg, SESSION_TOKEN_KEY);

        URL url = new URL(target);
        HttpURLConnection conn = null;
        try {
            log.info("Sending kill command for {}...", instanceId);
            conn = Http.delete(url, apiKey);

            int response = conn.getResponseCode();
            if (response == 404) {
                throw new IllegalArgumentException("Process not found: " + instanceId);
            }
            Http.assertOk(conn);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        boolean sync = (boolean) cfg.getOrDefault(SYNC_KEY, false);
        if (sync) {
            waitForCompletion(ctx, cfg, Collections.singletonList(instanceId), DEFAULT_KILL_TIMEOUT);
        }
    }

    private Map<String, Object> createJobCfg(Context ctx, Map<String, Object> job) throws Exception {
        Map<String, Object> m = createCfg(ctx, BASEURL_KEY, SYNC_KEY, ENTRY_POINT_KEY, ARCHIVE_KEY, PROJECT_KEY,
                REPOSITORY_KEY, ARGUMENTS_KEY, INSTANCE_ID_KEY, TAGS_KEY, DISABLE_ON_CANCEL_KEY,
                DISABLE_ON_FAILURE_KEY, OUT_VARS_KEY);

        if (job != null) {
            m.putAll(job);
        }

        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createRequest(Map<String, Object> cfg) {
        Map<String, Object> req = new HashMap<>();

        String entryPoint = (String) cfg.get(ENTRY_POINT_KEY);
        if (entryPoint != null) {
            req.put(Constants.Request.ENTRY_POINT_KEY, entryPoint);
        }

        Set<String> tags = getTags(cfg);
        if (tags != null) {
            req.put(Constants.Request.TAGS_KEY, tags);
        }

        Map<String, Object> args = (Map<String, Object>) cfg.get(ARGUMENTS_KEY);
        if (args != null) {
            req.put(Constants.Request.ARGUMENTS_KEY, new HashMap<>(args));
        }

        if (getBoolean(cfg, DISABLE_ON_CANCEL_KEY)) {
            req.put(Constants.Request.DISABLE_ON_CANCEL_KEY, true);
        }

        if (getBoolean(cfg, DISABLE_ON_FAILURE_KEY)) {
            req.put(Constants.Request.DISABLE_ON_FAILURE_KEY, true);
        }

        Collection<String> outVars = (Collection<String>) cfg.get(OUT_VARS_KEY);
        if (outVars != null && !outVars.isEmpty()) {
            req.put(Constants.Request.OUT_EXPRESSIONS_KEY, outVars);
        }

        return req;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getTags(Map<String, Object> cfg) {
        if (cfg == null) {
            return null;
        }

        Object v = cfg.get(TAGS_KEY);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return Collections.singleton((String) v);
        } else if (v instanceof String[]) {
            return new HashSet<>(Arrays.asList((String[]) v));
        } else if (v instanceof Collection) {
            return new HashSet<>((Collection) v);
        } else {
            throw new IllegalArgumentException("'" + TAGS_KEY + "' must a single string value or an array of strings: " + v);
        }
    }

    private static boolean getBoolean(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Boolean) {
            return (Boolean) v;
        } else if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        }
        return false;
    }

    private static Action getAction(Context ctx) {
        Object v = ctx.getVariable(ACTION_KEY);
        if (v instanceof String) {
            String s = (String) v;
            return Action.valueOf(s.trim().toUpperCase());
        }
        throw new IllegalArgumentException("'" + ACTION_KEY + "' must be a string");
    }

    private static int getInstances(Map<String, Object> cfg) {
        int i = 1;

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

    private enum Action {

        START,
        FORK,
        KILL
    }
}
