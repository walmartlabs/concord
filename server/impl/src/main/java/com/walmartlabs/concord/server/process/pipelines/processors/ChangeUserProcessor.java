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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.form.FormServiceV1;

import javax.inject.Inject;
import java.util.Map;

/**
 * Responsible for saving the current user's security subject when
 * the process resumes with {@code runAs.keep} form option.
 */
public class ChangeUserProcessor implements PayloadProcessor {

    private final ProcessSecurityContext securityContext;
    private final CurrentUserInfoProcessor currentUserInfoProcessor;

    @Inject
    public ChangeUserProcessor(ProcessSecurityContext securityContext,
                               CurrentUserInfoProcessor currentUserInfoProcessor) {
        this.securityContext = securityContext;
        this.currentUserInfoProcessor = currentUserInfoProcessor;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return chain.process(payload);
        }

        Map<String, Object> runAsParams = MapUtils.getMap(cfg, FormServiceV1.INTERNAL_RUN_AS_KEY, null);
        if (runAsParams == null) {
            return chain.process(payload);
        }

        boolean keep = MapUtils.getBoolean(runAsParams, Constants.Forms.RUN_AS_KEEP_KEY, false);
        if (keep) {
            securityContext.storeCurrentSubject(payload.getProcessKey());
            return currentUserInfoProcessor.process(chain, payload);
        }

        return chain.process(payload);
    }
}
