package com.walmartlabs.concord.client;

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.client.Keys.SESSION_TOKEN_KEY;

@Named("inventory")
public class InventoryTask extends AbstractConcordTask {

    private static final Logger log = LoggerFactory.getLogger(InventoryTask.class);

    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
                                       String orgName, String inventoryName,
                                       String hostGroupName, String queryName, Map<String, Object> params) throws Exception {
        return ansible(ctx, orgName, inventoryName, hostGroupName, queryName, params, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
           String orgName, String inventoryName,
                                       String hostGroupName, String queryName, Map<String, Object> params, Map<String, Object> vars) throws Exception {

        List<String> hostIps = execQuery(ctx, orgName, inventoryName, queryName, params, List.class);
        Map<String, Object> hostVars = new HashMap<>();
        hostVars.put("hosts", hostIps);
        if (vars != null && !vars.isEmpty()) {
            hostVars.put("vars", vars);
        }

        log.info("ansible ['{}', '{}', '{}', '{}', '{}'] -> {}", orgName, inventoryName, hostGroupName, queryName, params, hostIps != null);

        return Collections.singletonMap(hostGroupName, hostVars);
    }

    @SuppressWarnings("unchecked")
    public List<Object> query(@InjectVariable("context") Context ctx,
            String orgName, String inventoryName, String queryName, Map<String, Object> params) throws Exception {

        List<Object> result = execQuery(ctx, orgName, inventoryName, queryName, params, List.class);

        log.info("query ['{}', '{}', '{}', '{}'] -> {}", orgName, inventoryName, queryName, params, result != null);

        return result;
    }

    private <T> T execQuery(Context ctx, String orgName, String inventoryName, String queryName, Map<String, Object> params, Class<T> clazz) throws Exception {
        Map<String, Object> cfg = createTaskCfg(ctx);
        String target = String.format("%s/api/v1/org/%s/inventory/%s/query/%s/exec", get(cfg, Keys.BASEURL_KEY), orgName, inventoryName, queryName);
        String sessionToken = get(cfg, SESSION_TOKEN_KEY);

        URL url = new URL(target);
        HttpURLConnection conn = Http.postJson(url, sessionToken, params);

        return Http.read(conn, clazz);
    }

    private Map<String, Object> createTaskCfg(Context ctx) {
        return createCfg(ctx, Keys.BASEURL_KEY);
    }
}
