package com.walmartlabs.concord.server.security;

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


public enum Permission {

    /**
     * Read-only access to the process queue for all organizations.
     * <p>
     * As in {@code com/walmartlabs/concord/server/db/v1.18.0.xml}
     */
    GET_PROCESS_QUEUE_ALL_ORGS("getProcessQueueAllOrgs"),
    /**
     * Permission to create organizations
     * <p>
     * As in {@code com/walmartlabs/concord/server/db/v1.57.0.xml}
     */
    CREATE_ORG("createOrg"),
    /**
     * Permission to update organizations
     * <p>
     * As in {@code com/walmartlabs/concord/server/db/v1.94.0.xml}
     */
    UPDATE_ORG("updateOrg"),
    /**
     * Allows users to specify API key values when creating new API keys.
     * <p>
     * As in {@code com/walmartlabs/concord/server/db/v2.31.0.xml}
     */
    API_KEY_SPECIFY_VALUE("apiKeySpecifyValue"),
    /**
     * Permission to connect a websocket as an agent.
     * <p>
     * Implicitly granted to API keys without a user (e.g. the default agent
     * token, see {@code com/walmartlabs/concord/server/db/v2.21.0.xml}) since
     * such keys can't hold roles.
     */
    AGENT_WEBSOCKET("agentWebsocket", true),;

    private final String key;
    private final boolean grantedToUserlessKeys;

    Permission(String key) {
        this(key, false);
    }

    Permission(String key, boolean grantedToUserlessKeys) {
        this.key = key;
        this.grantedToUserlessKeys = grantedToUserlessKeys;
    }

    public String getKey() {
        return key;
    }

    public boolean isGrantedToUserlessKeys() {
        return grantedToUserlessKeys;
    }

    public boolean isPermitted() {
        return SecurityUtils.isPermitted(this.getKey());
    }
}
