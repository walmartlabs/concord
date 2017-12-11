package com.walmartlabs.concord.client;

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import static com.walmartlabs.concord.client.Keys.SESSION_TOKEN_KEY;
import static com.walmartlabs.concord.client.Keys.BASEURL_KEY;

@Named("landingPageRefresh")
public class LandingPageRefreshTask extends AbstractConcordTask implements Task {

    private static final String ORG_KEY = "org";
    private static final String REPOSITORY_KEY = "repository";
    private static final String PROJECT_KEY = "project";

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> cfg = createCfg(ctx, BASEURL_KEY, ORG_KEY, REPOSITORY_KEY, PROJECT_KEY);
        String orgName = get(cfg, ORG_KEY);
        String projectName = get(cfg, PROJECT_KEY);
        String repositoryName = get(cfg, REPOSITORY_KEY);

        String target = get(cfg, BASEURL_KEY) + "/api/v1/org/" + orgName + "/landing_page/refresh/" + projectName + "/" + repositoryName;
        String sessionToken = get(cfg, SESSION_TOKEN_KEY);

        URL url = new URL(target);
        Http.postJson(url, sessionToken, Collections.emptyMap());
    }
}
