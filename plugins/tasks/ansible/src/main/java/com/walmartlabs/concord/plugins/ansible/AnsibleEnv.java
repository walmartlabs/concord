package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnsibleEnv {

    private static final Logger log = LoggerFactory.getLogger(AnsibleEnv.class);

    private final UUID instanceId;
    private final String sessionToken;
    private final UUID eventCorrelationId;
    private final String orgName;
    private final Integer retryCount;
    private final boolean debug;

    private final ApiConfiguration apiCfg;

    private Map<String, String> env = Collections.emptyMap();

    public AnsibleEnv(AnsibleContext context, ApiConfiguration apiCfg) {
        this.instanceId = context.instanceId();
        this.sessionToken = context.sessionToken();
        this.eventCorrelationId = context.eventCorrelationId();
        this.orgName = context.orgName();
        this.retryCount = context.retryCount();
        this.debug = context.debug();
        this.apiCfg = apiCfg;
    }

    public AnsibleEnv parse(Map<String, Object> args) {
        env = mergeEnv(defaultEnv(), concordEnv(), args);

        if (eventCorrelationId != null) {
            env.put("CONCORD_EVENT_CORRELATION_ID", eventCorrelationId.toString());
        }

        if (retryCount != null) {
            env.put("CONCORD_CURRENT_RETRY_COUNT", Integer.toString(retryCount));
        }

        return this;
    }

    public void write() {
        if (debug) {
            StringBuilder b = new StringBuilder();
            env.forEach((k, v) -> b.append(k).append("=").append(v).append('\n'));
            log.info("Using environment: {}", b.toString());
        }
    }

    public Map<String, String> get() {
        return env;
    }

    public AnsibleEnv put(String key, String value) {
        env.put(key, value);
        return this;
    }

    /**
     * Overridable environment variables.
     */
    private Map<String, String> defaultEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("ANSIBLE_FORCE_COLOR", "true");
        return env;
    }

    /**
     * Non-overridable environment variables.
     */
    private Map<String, String> concordEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("CONCORD_INSTANCE_ID", instanceId.toString());
        env.put("CONCORD_BASE_URL", apiCfg.getBaseUrl());

        if (sessionToken != null) {
            env.put("CONCORD_SESSION_TOKEN", sessionToken);
        }

        env.put("CONCORD_POLICY", Paths.get(Constants.Files.CONCORD_SYSTEM_DIR_NAME, Constants.Files.POLICY_FILE_NAME).toString());

        if (orgName != null) {
            env.put("CONCORD_CURRENT_ORG_NAME", orgName);
        }

        return env;
    }

    private static Map<String, String> mergeEnv(Map<String, String> defaultEnv, Map<String, String> concordEnv, Map<String, Object> args) {
        Map<String, Object> extraEnv = MapUtils.getMap(args, TaskParams.EXTRA_ENV_KEY, Collections.emptyMap());

        Map<String, String> result = new HashMap<>(defaultEnv.size() + concordEnv.size() + extraEnv.size());
        result.putAll(defaultEnv);

        extraEnv.forEach((k, v) -> result.put(k, v.toString()));

        result.putAll(concordEnv);
        return result;
    }

}
