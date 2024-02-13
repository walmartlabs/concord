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
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NodeRosterTaskUtils {

    private static final Logger log = LoggerFactory.getLogger(NodeRosterTaskUtils.class);

    public static String getBaseUrl(Map<String, Object> cfg) {
        return MapUtils.getString(cfg, Constants.BASE_URL_KEY);
    }

    public static String getApiKey(Map<String, Object> cfg) {
        return MapUtils.getString(cfg, Constants.API_KEY);
    }

    public static int getLimit(Map<String, Object> cfg) {
        return MapUtils.getInt(cfg, Constants.LIMIT_KEY, 30);
    }

    public static int getOffset(Map<String, Object> cfg) {
        return MapUtils.getInt(cfg, Constants.OFFSET_KEY, 0);
    }

    public static String getHostName(Map<String, Object> cfg) {
        return MapUtils.getString(cfg, Constants.HOSTNAME_KEY);
    }

    public static UUID getHostId(Map<String, Object> cfg) {
        return MapUtils.getUUID(cfg, Constants.HOSTID_KEY);
    }

    public static Action getAction(Map<String, Object> cfg) {
        String action = MapUtils.assertString(cfg, Constants.ACTION_KEY);
        return Action.valueOf(action.trim().toUpperCase());
    }

    public enum Action {
        HOSTSWITHARTIFACTS,
        FACTS,
        DEPLOYEDONHOST
    }

    /**
     * Find facts for a given host name or host id
     */
    public static Object findFacts(NodeRosterFactsApi api,
                                   Map<String, Object> paramsCfg) throws Exception {
        String hostName = getHostName(paramsCfg);
        UUID hostId = getHostId(paramsCfg);

        if (hostName != null) {
            log.info("Finding facts for hostname {}", hostName);
        } else if (hostId != null) {
            log.info("Finding facts for host id {}", hostId);
        } else {
            throw new IllegalArgumentException("A 'hostName' or 'hostId' value is required");
        }

        return ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () ->
                api.getFacts(hostId, hostName));
    }

    /**
     * Find hosts with a deployed artifact
     */
    public static List<HostEntry> findHostsWithArtifacts(NodeRosterHostsApi api,
                                                         Map<String, Object> paramsCfg) throws Exception {

        String artifactPattern = MapUtils.assertString(paramsCfg, Constants.ARTIFACT_PATTERN_KEY);
        int limit = getLimit(paramsCfg);
        int offset = getOffset(paramsCfg);

        log.info("Finding hosts where artifact {} is deployed " +
                "(limit: {}, offset: {})...", artifactPattern, limit, offset);

        return ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () ->
                api.listKnownHosts(null, artifactPattern, null, null, limit, offset));
    }

    /**
     * Find artifacts deployed on a given host
     */
    public static List<ArtifactEntry> findDeployedArtifacts(NodeRosterArtifactsApi api,
                                                            Map<String, Object> paramsCfg) throws Exception {

        String hostName = getHostName(paramsCfg);
        UUID hostId = getHostId(paramsCfg);
        int limit = getLimit(paramsCfg);
        int offset = getOffset(paramsCfg);

        if (hostName == null && hostId == null) {
            throw new IllegalArgumentException("A 'hostName' or 'hostId' value is required");
        }

        log.info("Finding artifacts deployed on a host (hostName: {}, hostId: {})...", hostName, hostId);

        return ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () ->
                api.listHostArtifacts(hostId, hostName, null, limit, offset));
    }
}
