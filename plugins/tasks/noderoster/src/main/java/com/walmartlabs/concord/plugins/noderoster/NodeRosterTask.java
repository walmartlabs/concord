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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.sdk.ContextUtils.*;

@Named("nodeRoster")
public class NodeRosterTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(NodeRosterTask.class);
    private static final String API_KEY = "apiKey";

    @Inject
    ApiClientFactory apiClientFactory;

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);

        // TODO retries

        switch (action) {
            case HOSTSWITHARTIFACTS: {
                findHostsWithArtifacts(ctx);
                break;
            }
            case FACTS: {
                findFacts(ctx);
                break;
            }

            case DEPLOYEDONHOST: {
                findDeployedArtifacts(ctx);
                break;
            }

            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    /**
     * Find facts for a given host name or host id
     */
    public void findFacts(Context ctx) throws Exception {
        String hostName = getString(ctx, Keys.HOSTNAME_KEY);
        UUID hostId = getUUID(ctx, Keys.HOSTID_KEY);

        if (hostName != null) {
            log.info("Finding facts for hostname {}", hostName);
        } else if (hostId != null) {
            log.info("Finding facts for host id {}", hostId);
        } else {
            throw new IllegalArgumentException("A 'hostName' or 'hostId' value is required");
        }

        Object facts = withClient(ctx, client -> {
            NodeRosterFactsApi api = new NodeRosterFactsApi(client);
            return api.getFacts(hostId, hostName);
        });

        ctx.setVariable("result", toResult(facts));
    }

    /**
     * Find hosts with a deployed artifact
     */
    public void findHostsWithArtifacts(Context ctx) throws Exception {
        String artifactPattern = assertString(ctx, Keys.ARTIFACT_PATTERN_KEY);
        int limit = getLimit(ctx);
        int offset = getOffset(ctx);

        log.info("Finding hosts where artifact {} is deployed (limit: {}, offset: {})...", artifactPattern, limit, offset);

        List<HostEntry> hosts = withClient(ctx, client -> {
            NodeRosterHostsApi api = new NodeRosterHostsApi(client);
            return api.list(null, artifactPattern, null, null, limit, offset);
        });

        ctx.setVariable("result", toResult(hosts));
    }

    /**
     * Find artifacts deployed on a given host
     */
    public void findDeployedArtifacts(Context ctx) throws Exception {
        String hostName = getString(ctx, Keys.HOSTNAME_KEY);
        UUID hostId = getUUID(ctx, Keys.HOSTID_KEY);
        int limit = getLimit(ctx);
        int offset = getOffset(ctx);

        if (hostName == null && hostId == null) {
            throw new IllegalArgumentException("A 'hostName' or 'hostId' value is required");
        }

        log.info("Finding artifacts deployed on a host (hostName: {}, hostId: {})...", hostName, hostId);

        List<ArtifactEntry> deployedArtifacts = withClient(ctx, client -> {
            NodeRosterArtifactsApi api = new NodeRosterArtifactsApi(client);
            return api.list(hostId, hostName, null, limit, offset);
        });

        ctx.setVariable("result", toResult(deployedArtifacts));
    }

    private <T> T withClient(Context ctx, CheckedFunction<ApiClient, T> f) throws Exception {
        ApiClientConfiguration cfg = ApiClientConfiguration.builder()
                .baseUrl(getBaseUrl(ctx))
                .apiKey(getApiKey(ctx))
                .context(ctx)
                .build();
        return f.apply(apiClientFactory.create(cfg));
    }

    private Object toResult(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", data != null);

//        if (data != null) {
//            data = objectMapper.convertValue(data, Object.class);
//        }
        m.put("data", data);

        return m;
    }

    private static String getBaseUrl(Context ctx) {
        return ContextUtils.getString(ctx, Keys.BASE_URL_KEY);
    }

    private static String getApiKey(Context ctx) {
        return (String) ctx.getVariable(API_KEY);
    }

    private static int getLimit(Context ctx) {
        return ContextUtils.getInt(ctx, Keys.LIMIT_KEY, 30);
    }

    private static int getOffset(Context ctx) {
        return ContextUtils.getInt(ctx, Keys.OFFSET_KEY, 0);
    }

    private static Action getAction(Context ctx) {
        String action = assertString(ctx, Keys.ACTION_KEY);
        return Action.valueOf(action.trim().toUpperCase());
    }

    private enum Action {
        HOSTSWITHARTIFACTS,
        FACTS,
        DEPLOYEDONHOST
    }

    @FunctionalInterface
    protected interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
