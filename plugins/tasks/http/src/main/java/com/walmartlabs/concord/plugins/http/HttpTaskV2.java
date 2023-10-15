package com.walmartlabs.concord.plugins.http;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;

@Named("http")
public class HttpTaskV2 implements Task {

    private final Context context;

    @Inject
    public HttpTaskV2(Context context) {
        this.context = context;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        Configuration config = Configuration.custom().build(context.workingDirectory().toString(), input.toMap(), context.processConfiguration().debug());

        Map<String, Object> response = new HashMap<>(SimpleHttpClient.create(config).execute().getResponse());
        return TaskResult.of((boolean)response.remove("success"), (String)response.remove("errorString"), response);
    }
}
