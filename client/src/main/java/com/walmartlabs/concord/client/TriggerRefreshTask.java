package com.walmartlabs.concord.client;

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;
import com.walmartlabs.concord.server.api.org.trigger.TriggerResource;

import javax.inject.Named;
import javax.ws.rs.core.Response;
import java.util.Map;

@Named("triggersRefresh")
public class TriggerRefreshTask extends AbstractConcordTask implements Task {

    private static final String ORG_KEY = "org";
    private static final String REPOSITORY_KEY = "repository";
    private static final String PROJECT_KEY = "project";

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> cfg = createCfg(ctx, ORG_KEY, REPOSITORY_KEY, PROJECT_KEY);
        String orgName = get(cfg, ORG_KEY);
        String projectName = get(cfg, PROJECT_KEY);
        String repositoryName = get(cfg, REPOSITORY_KEY);

        Response resp = withClient(ctx, target -> {
            TriggerResource proxy = target.proxy(TriggerResource.class);
            return proxy.refresh(orgName, projectName, repositoryName);
        });

        int response = resp.getStatus();
        if (response < 200 || response >= 300) {
            throw new RuntimeException("Triggers refresh error: " + resp.getStatus());
        }
    }
}
