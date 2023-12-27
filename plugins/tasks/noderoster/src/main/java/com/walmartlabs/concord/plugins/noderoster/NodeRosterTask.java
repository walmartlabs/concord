package com.walmartlabs.concord.plugins.noderoster;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.plugins.noderoster.NodeRosterTaskUtils.*;
import static com.walmartlabs.concord.plugins.noderoster.Result.createResponse;

@Named("nodeRoster")
public class NodeRosterTask implements Task {

    private final ApiClientFactory apiClientFactory;

    @Inject
    public NodeRosterTask(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> paramsCfg = createParamsCfg(ctx);
        Action action = getAction(paramsCfg);

        switch (action) {
            case HOSTSWITHARTIFACTS: {
                findHostsWithArtifacts(ctx, paramsCfg);
                break;
            }
            case FACTS: {
                findFacts(ctx, paramsCfg);
                break;
            }
            case DEPLOYEDONHOST: {
                findDeployedArtifacts(ctx, paramsCfg);
                break;
            }

            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    /**
     * Find facts for a given host name or host id
     */
    public void findFacts(Context ctx, Map<String, Object> paramsCfg) throws Exception {
        ApiClient clientConfig = clientConfig(ctx, paramsCfg);
        NodeRosterFactsApi api = new NodeRosterFactsApi(clientConfig);
        Object facts = NodeRosterTaskUtils.findFacts(api, paramsCfg);

        ctx.setVariable("result", createResponse(facts));
    }

    /**
     * Find hosts with a deployed artifact
     */
    public void findHostsWithArtifacts(Context ctx, Map<String, Object> paramsCfg) throws Exception {
        ApiClient clientConfig = clientConfig(ctx, paramsCfg);
        NodeRosterHostsApi api = new NodeRosterHostsApi(clientConfig);
        List<HostEntry> hosts = NodeRosterTaskUtils.findHostsWithArtifacts(api, paramsCfg);

        ctx.setVariable("result", createResponse(hosts));
    }

    /**
     * Find artifacts deployed on a given host
     */
    public void findDeployedArtifacts(Context ctx, Map<String, Object> paramsCfg) throws Exception {
        ApiClient clientConfig = clientConfig(ctx, paramsCfg);
        NodeRosterArtifactsApi api = new NodeRosterArtifactsApi(clientConfig);
        List<ArtifactEntry> deployedArtifacts = NodeRosterTaskUtils.findDeployedArtifacts(api, paramsCfg);

        ctx.setVariable("result", createResponse(deployedArtifacts));
    }

    private ApiClient clientConfig(Context ctx, Map<String, Object> paramsCfg) {
        return apiClientFactory.create(ApiClientConfiguration.builder()
                .baseUrl(getBaseUrl(paramsCfg))
                .apiKey(getApiKey(paramsCfg))
                .sessionToken(ContextUtils.sessionTokenOrNull(ctx))
                .build());
    }

    private static Map<String, Object> createParamsCfg(Context ctx) {
        Map<String, Object> m = new HashMap<>();
        for (String k : Constants.ALL_IN_PARAMS) {
            Object v = ctx.getVariable(k);
            if (v != null) {
                m.put(k, v);
            }
        }
        return m;
    }
}
