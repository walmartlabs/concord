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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.plugins.noderoster.NodeRosterTaskUtils.getAction;

@Named("nodeRoster")
public class NodeRosterTaskV2 implements Task {

    private final ApiClient apiClient;

    @Inject
    public NodeRosterTaskV2(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        Map<String, Object> paramsCfg = input.toMap();
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
    public TaskResult findFacts(Map<String, Object> paramsCfg) throws Exception {
        NodeRosterFactsApi api = new NodeRosterFactsApi(apiClient);
        Object facts = NodeRosterTaskUtils.findFacts(api, paramsCfg);
        return result("facts", facts);
    }

    /**
     * Find hosts with a deployed artifact
     */
    public TaskResult findHostsWithArtifacts(Map<String, Object> paramsCfg) throws Exception {
        NodeRosterHostsApi api = new NodeRosterHostsApi(apiClient);
        List<HostEntry> hosts = NodeRosterTaskUtils.findHostsWithArtifacts(api, paramsCfg);
        return result("hosts", hosts);
    }

    /**
     * Find artifacts deployed on a given host
     */
    public TaskResult findDeployedArtifacts(Map<String, Object> paramsCfg) throws Exception {
        NodeRosterArtifactsApi api = new NodeRosterArtifactsApi(apiClient);
        List<ArtifactEntry> deployedArtifacts = NodeRosterTaskUtils.findDeployedArtifacts(api, paramsCfg);
        return result("artifacts", deployedArtifacts);
    }

    private static TaskResult result(String key, Object data) {
        return TaskResult.of(data != null)
                .value(key, data);
    }
}
