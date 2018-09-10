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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.sdk.ContextUtils.getMap;
import static com.walmartlabs.concord.sdk.ContextUtils.getString;

public class AnsibleEnv {

    private static final Logger log = LoggerFactory.getLogger(AnsibleEnv.class);

    private final String txId;
    private final boolean debug;

    private final Context context;
    private final ApiConfiguration apiCfg;

    private Map<String, String> env = Collections.emptyMap();

    public AnsibleEnv(Context context, ApiConfiguration apiCfg, boolean debug) {
        this.txId = getString(context, Constants.Context.TX_ID_KEY);
        this.context = context;
        this.apiCfg = apiCfg;
        this.debug = debug;
    }

    public AnsibleEnv parse(Map<String, Object> args) {
        env = mergeEnv(defaultEnv(), concordEnv(), args);
        UUID eventCorrelationId = context.getEventCorrelationId();
        if (eventCorrelationId != null) {
            env.put("CONCORD_EVENT_CORRELATION_ID", eventCorrelationId.toString());
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

    /**
     * Overridable environment variables.
     * @return
     */
    private Map<String, String> defaultEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("ANSIBLE_FORCE_COLOR", "true");
        return env;
    }

    /**
     * Non-overridable environment variables.
     * @return
     */
    private Map<String, String> concordEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("CONCORD_INSTANCE_ID", txId);
        env.put("CONCORD_BASE_URL", apiCfg.getBaseUrl());

        String t = apiCfg.getSessionToken(context);
        env.put("CONCORD_SESSION_TOKEN", t != null ? t : "none");

        env.put("CONCORD_POLICY", Paths.get(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME, InternalConstants.Files.POLICY_FILE_NAME).toString());

        Map<String, Object> projectInfo = getMap(context, Constants.Request.PROJECT_INFO_KEY, null);
        String orgName = projectInfo != null ? (String) projectInfo.get("orgName") : null;
        if (orgName != null) {
            env.put("CONCORD_CURRENT_ORG_NAME", orgName);
        }

        return env;
    }

    private static Map<String, String> mergeEnv(Map<String, String> defaultEnv, Map<String, String> concordEnv, Map<String, Object> args) {
        Map<String, Object> extraEnv = ArgUtils.getMap(args, TaskParams.EXTRA_ENV_KEY);
        if (extraEnv == null || extraEnv.isEmpty()) {
            return concordEnv;
        }

        Map<String, String> result = new HashMap<>(defaultEnv.size() + concordEnv.size() + extraEnv.size());
        result.putAll(defaultEnv);

        extraEnv.forEach((k, v) -> {
            result.put(k, v.toString());
        });

        result.putAll(concordEnv);
        return result;
    }

}
