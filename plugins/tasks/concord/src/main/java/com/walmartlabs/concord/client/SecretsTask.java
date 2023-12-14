package com.walmartlabs.concord.client;

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

import com.walmartlabs.concord.client.v1.ContextBackedVariables;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.Map;

@Named("concordSecrets")
public class SecretsTask implements Task {

    public static final String RESULT_KEY = "result";

    private final ApiClientFactory clientFactory;

    @Inject
    public SecretsTask(ApiClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public void execute(Context ctx) throws Exception {
        ApiClientConfiguration c = ApiClientConfiguration.builder()
                .sessionToken(ContextUtils.getSessionToken(ctx))
                .build();

        SecretsTaskParams in = SecretsTaskParams.of(new ContextBackedVariables(ctx));
        TaskResult.SimpleResult result = new SecretsTaskCommon(clientFactory.create(c), getProcessOrgName(ctx))
                .execute(in);
        ctx.setVariable(RESULT_KEY, result.toMap());
    }

    private static String getProcessOrgName(Context ctx) {
        Map<String, Object> projectInfo = ContextUtils.getMap(ctx, Constants.Request.PROJECT_INFO_KEY, Collections.emptyMap());
        return (String) projectInfo.get("orgName");
    }
}
