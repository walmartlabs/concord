package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.process.loader.model.Profile;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;

import javax.inject.Inject;
import java.util.*;

public class EntryPointProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;

    @Inject
    public EntryPointProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        String s = payload.getHeader(Payload.ENTRY_POINT);

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        if (s == null) {
            s = (String) cfg.get(Constants.Request.ENTRY_POINT_KEY);
        }

        if (s == null) {
            s = Constants.Request.DEFAULT_ENTRY_POINT_NAME;
        }

        if (!isValidEntryPoint(payload, s)) {
            throw new ProcessException(
                    payload.getProcessKey(),
                    String.format("entryPoint '%s' is not a public flow", s)
            );
        }

        cfg.put(Constants.Request.ENTRY_POINT_KEY, s);
        payload = payload.putHeader(Payload.CONFIGURATION, cfg)
                .putHeader(Payload.ENTRY_POINT, s);

        logManager.info(payload.getProcessKey(), "Using entry point: {}", s);

        return chain.process(payload);
    }

    /**
     * Determines validity of a given {@code entryPoint} based on
     * the currently active profiles.
     *
     * @param payload    payload containing public flows definition
     * @param entryPoint process {@code entryPoint} flow
     * @return true if {@code entryPoint} is a valid value
     */
    private static boolean isValidEntryPoint(Payload payload, String entryPoint) {
        ProcessDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);

        Set<String> publicFlows = new HashSet<>(pd.publicFlows() != null ? pd.publicFlows() : Collections.emptySet());

        List<String> activeProfiles = payload.getHeader(Payload.ACTIVE_PROFILES);
        if (activeProfiles != null) {
            for (String profileName : activeProfiles) {
                Profile p = pd.profiles().get(profileName);
                if (p == null || p.publicFlows() == null) {
                    continue;
                }

                publicFlows.addAll(p.publicFlows());
            }
        }

        if (publicFlows.isEmpty()) {
            // all flows are public when public definition not provided
            return true;
        }

        return publicFlows.contains(entryPoint);
    }
}
