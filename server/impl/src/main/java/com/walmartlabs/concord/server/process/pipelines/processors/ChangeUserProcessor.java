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


import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.ConcordFormService;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@Named
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
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            return chain.process(payload);
        }

        Map<String, Object> runAsParams = (Map<String, Object>) cfg.get(ConcordFormService.INTERNAL_RUN_AS_KEY);
        if (runAsParams == null) {
            return chain.process(payload);
        }

        Boolean keep = (Boolean) runAsParams.get(InternalConstants.Forms.RUN_AS_KEEP_KEY);
        if (keep != null && keep) {
            securityContext.storeCurrentSubject(payload.getInstanceId());
            return currentUserInfoProcessor.process(chain, payload);
        }

        return chain.process(payload);
    }
}
