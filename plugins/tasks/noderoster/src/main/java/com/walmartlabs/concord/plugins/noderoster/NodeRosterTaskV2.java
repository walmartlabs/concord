package com.walmartlabs.concord.plugins.noderoster;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.plugins.noderoster.NodeRosterTaskUtils.getAction;
import static com.walmartlabs.concord.plugins.noderoster.Result.createResponse;

@Named("nodeRoster")
public class NodeRosterTaskV2 implements Task {

    private final ApiClient apiClient;

    @Inject
    public NodeRosterTaskV2(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public Serializable execute(TaskContext ctx) throws Exception {
        Map<String, Object> paramsCfg = ctx.input();
        NodeRosterTaskUtils.Action action = getAction(paramsCfg);

        switch (action) {
            case HOSTSWITHARTIFACTS: {
                return findHostsWithArtifacts(paramsCfg);
            }
            case FACTS: {
                return findFacts(paramsCfg);
            }
            case DEPLOYEDONHOST: {
                return findDeployedArtifacts(paramsCfg);
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    /**
     * Find facts for a given host name or host id
     */
    public Result findFacts(Map<String, Object> paramsCfg) throws Exception {
        NodeRosterFactsApi api = new NodeRosterFactsApi(apiClient);
        Object facts = NodeRosterTaskUtils.findFacts(api, paramsCfg);
        return createResponse(facts);
    }

    /**
     * Find hosts with a deployed artifact
     */
    public Result findHostsWithArtifacts(Map<String, Object> paramsCfg) throws Exception {
        NodeRosterHostsApi api = new NodeRosterHostsApi(apiClient);
        List<HostEntry> hosts = NodeRosterTaskUtils.findHostsWithArtifacts(api, paramsCfg);
        return createResponse(hosts);
    }

    /**
     * Find artifacts deployed on a given host
     */
    public Result findDeployedArtifacts(Map<String, Object> paramsCfg) throws Exception {
        NodeRosterArtifactsApi api = new NodeRosterArtifactsApi(apiClient);
        List<ArtifactEntry> deployedArtifacts = NodeRosterTaskUtils.findDeployedArtifacts(api, paramsCfg);
        return createResponse(deployedArtifacts);
    }
}
