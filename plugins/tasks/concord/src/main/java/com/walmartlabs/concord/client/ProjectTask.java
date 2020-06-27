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
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;

import javax.inject.Named;
import java.util.Collections;
import java.util.Map;

@Named("project")
public class ProjectTask extends AbstractConcordTask {

    @Override
    public void execute(Context ctx) throws Exception {
        ProjectTaskParams in = new ProjectTaskParams(new ContextBackedVariables(ctx));

        withClient(ctx, client -> {
            new ProjectTaskCommon(client, getProcessOrgName(ctx)).execute(in);
            return null;
        });
    }

    private static String getProcessOrgName(Context ctx) {
        Map<String, Object> projectInfo = ContextUtils.getMap(ctx, Constants.Request.PROJECT_INFO_KEY, Collections.emptyMap());
        return (String) projectInfo.get("orgName");
    }
}
