package com.walmartlabs.concord.plugins.jenkins;

import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.JavaDelegate;

import javax.inject.Named;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import java.util.HashMap;
import java.util.Map;

/**
 * Parameters:
 * <ul>
 * <li>jenkins_url</li>
 * </ul>
 * Rest of the parameters will be passed as they are.
 */
@Named
public class TriggerJobTask implements JavaDelegate, Task {

    public static final String JENKINS_URL_KEY = "jenkins_url";

    @Override
    public String getKey() {
        return "jenkins";
    }

    @Override
    public void execute(ExecutionContext ctx) throws Exception {
        String jenkinsUrl = getNonEmpty(ctx, JENKINS_URL_KEY);

        Map<String, Object> m = new HashMap<>(ctx.getVariables());
        m.remove(JENKINS_URL_KEY);

        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget t = client.target(jenkinsUrl);
            for (Map.Entry<String, Object> e : m.entrySet()) {
                t = t.queryParam(e.getKey(), e.getValue().toString());
            }
            t.request().post(Entity.json(null));
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private static String getNonEmpty(ExecutionContext ctx, String key) throws BpmnError {
        String s = (String) ctx.getVariable(key);
        if (s == null || s.trim().isEmpty()) {
            throw new BpmnError("Expected a non-empty string: " + key);
        }
        return s;
    }
}
