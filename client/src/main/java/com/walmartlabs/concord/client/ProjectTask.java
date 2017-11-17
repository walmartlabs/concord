package com.walmartlabs.concord.client;

import com.walmartlabs.concord.sdk.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.client.Keys.*;

@Named("project")
public class ProjectTask extends AbstractConcordTask {

    private static final Logger log = LoggerFactory.getLogger(ProjectTask.class);

    private static final String NAME_KEY = "name";
    private static final String REPOSITORIES_KEY = "repositories";

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);
        switch (action) {
            case CREATE: {
                create(ctx);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    @SuppressWarnings("unchecked")
    private void create(Context ctx) throws Exception {
        Map<String, Object> cfg = createCfg(ctx, NAME_KEY, REPOSITORIES_KEY);

        Map<String, Object> req = new HashMap<>();
        req.put("name", get(cfg, NAME_KEY));

        Object repos = cfg.get(REPOSITORIES_KEY);
        if (repos instanceof Collection) {
            req.put("repositories", toRepositories((Collection<Object>) repos));
        } else if (repos != null) {
            throw new IllegalArgumentException("'" + REPOSITORIES_KEY + "' must a list of repositories");
        }

        String target = get(cfg, BASEURL_KEY) + "/api/v1/project";
        String sessionToken = get(cfg, SESSION_TOKEN_KEY);

        URL url = new URL(target);
        HttpURLConnection conn = null;
        try {
            conn = Http.postJson(url, sessionToken, req);
            Map<String, Object> resp = Http.readMap(conn);
            log.info("The project was created (or updated): {}", resp);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toRepositories(Collection<Object> items) {
        Map<String, Object> m = new HashMap<>();

        for (Object i : items) {
            if (!(i instanceof Map)) {
                throw new IllegalArgumentException("Repository entry must be an object. Got: " + i);
            }

            Map<String, Object> r = (Map<String, Object>) i;

            String name = (String) r.get(NAME_KEY);
            if (name == null) {
                throw new IllegalArgumentException("Repository name is required");
            }

            m.put(name, r);
        }

        return m;
    }

    private static Action getAction(Context ctx) {
        Object v = ctx.getVariable(ACTION_KEY);
        if (v instanceof String) {
            String s = (String) v;
            return Action.valueOf(s.trim().toUpperCase());
        }
        throw new IllegalArgumentException("'" + ACTION_KEY + "' must be a string");
    }

    private enum Action {

        CREATE
    }
}
