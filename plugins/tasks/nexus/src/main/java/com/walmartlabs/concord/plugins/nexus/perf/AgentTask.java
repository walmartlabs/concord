package com.walmartlabs.concord.plugins.nexus.perf;

import com.sonatype.nexus.perftest.Duration;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Named("perf")
public class AgentTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(AgentTask.class);
    private static final int DEFAULT_AGENTS_COUNT = 1;
    private static final String DURATION_KEY = "test.duration";
    private static final String DEFAULT_DURATION = "15 MINUTES";

    private final AgentPoolHolder poolHolder;

    @Inject
    public AgentTask(AgentPoolHolder poolHolder) {
        this.poolHolder = poolHolder;
    }

    public void connect(String txId, String[] urls) {
        poolHolder.connect(txId, urls);
        log.debug("create ['{}', {}] -> done", txId, urls);
    }

    public void loadAndStart(String txId, String scenarioDirTemplate, Map<String, Object> globals, Collection<Map<String, Object>> scenarios, String nexusUrl, Collection<String> memberUrls, String nexusUsername, String nexusPassword) {
        log.info("Loading nexus-perf scenarios: {}", scenarios);
        log.info("Target URL: {}", nexusUrl);
        log.info("Nexus cluster members: {}", memberUrls);

        try {
            AgentPoolManager m = poolHolder.get(txId);

            // TODO constants
            if (globals == null) {
                globals = new HashMap<>();
            }
            globals.put("nexus.baseurl", nexusUrl);
            if (memberUrls != null && !memberUrls.isEmpty()) {
                globals.put("nexus.memberurls", String.join(",", memberUrls));
            }
            globals.put("nexus.username", nexusUsername);
            globals.put("nexus.password", nexusPassword);

            for (Map<String, Object> s : scenarios) {
                String name = (String) s.get("name");
                String scenario = String.format(scenarioDirTemplate, name);
                int agents = (int) s.getOrDefault("agents", DEFAULT_AGENTS_COUNT);

                // copy overridable values
                Map<String, String> overrides = new HashMap<>();
                globals.forEach((k, v) -> overrides.put(k, v.toString()));

                for (Map.Entry<String, Object> e : s.entrySet()) {
                    String k = e.getKey();
                    if (k.startsWith("template.") || k.startsWith("test.") || k.startsWith("perftest.")) {
                        overrides.put(k, e.getValue().toString());
                    }
                }

                m.load(scenario, agents, overrides);
                m.start();

                String duration = (String) s.get(DURATION_KEY);
                log.debug("loadAndStart ['{}'] -> started {} agent(s) (scenario '{}', duration: '{}')", txId, agents, scenario, duration != null ? duration : "default");
            }
        } catch (Exception e) {
            throw new BpmnError("loadAndStartError", e);
        }
    }

    public void waitToStart(String txId, long timeout) {
        try {
            AgentPoolManager m = poolHolder.get(txId);
            m.waitToStart(timeout);
            log.debug("waitToStart ['{}', {}] -> done", txId, timeout);
        } catch (Exception e) {
            log.error("waitToStart ['{}', {}] -> error", txId, timeout);
            throw new BpmnError("waitToStartError");
        }
    }

    public void waitToFinish(String txId, long timeout, boolean stopOnFailure) {
        try {
            AgentPoolManager m = poolHolder.get(txId);
            m.waitToFinish(timeout, stopOnFailure);
            log.debug("waitToFinish ['{}', {}] -> done", txId, timeout);
        } catch (Exception e) {
            log.error("waitToFinish ['{}', {}] -> error", txId, timeout, e);
            throw new BpmnError("waitToFinishError", e);
        }
    }

    public String[] resolveHost(String host, int port) {
        try {
            InetAddress[] as = InetAddress.getAllByName(host);
            String[] result = new String[as.length];
            for (int i = 0; i < as.length; i++) {
                result[i] = as[i].getHostAddress() + ":" + port;
            }
            return result;
        } catch (Exception e) {
            log.error("resolveHost ['{}', {}] -> error", host, port, e);
            throw new BpmnError("resolveHostError", e);
        }
    }

    public void release(String txId) {
        try {
            poolHolder.close(txId);
            log.debug("release ['{}'] -> done", txId);
        } catch (Exception e) {
            log.error("release ['{}'] -> error", txId, e);
            throw new BpmnError("releaseError", e);
        }
    }

    public boolean hasFailures(String txId) {
        try {
            AgentPoolManager m = poolHolder.get(txId);
            if (m == null) {
                return false;
            }

            boolean result = m.hasFailures();
            log.debug("hasFailures ['{}'] -> {}", txId, result);
            return result;
        } catch (Exception e) {
            log.error("hasFailures ['{}'] -> error", e);
            throw new BpmnError("hasFailuresError", e);
        }
    }

    public long maxTimeout(Collection<Map<String, Object>> scenarios) {
        long result = 0;

        for (Map<String, Object> s : scenarios) {
            String v = (String) s.getOrDefault(DURATION_KEY, DEFAULT_DURATION);
            Duration d = new Duration(v);
            long t = d.toMillis();
            if (result < t) {
                result = t;
            }
        }

        return result + (15 * 60 * 1000); // additional 15 minutes
    }
}
