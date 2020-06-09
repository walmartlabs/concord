package com.walmartlabs.concord.plugins.docker.v2;

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

import com.walmartlabs.concord.plugins.docker.DockerTaskCommon;
import com.walmartlabs.concord.plugins.docker.Result;
import com.walmartlabs.concord.plugins.docker.TaskParams;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.HashMap;

@Named("docker")
public class DockerTaskV2 implements Task {

    private static final String LOG_OUTPUT_KEY = "logOutput";

    private final DockerTaskCommon delegate;

    @Inject
    public DockerTaskV2(com.walmartlabs.concord.runtime.v2.sdk.DockerService dockerService,
                        com.walmartlabs.concord.runtime.v2.sdk.FileService fileService,
                        Context context) {
        this.delegate = new DockerTaskCommon(context.workingDirectory(), fileService::createTempFile,
                (spec, outCallback, errCallback) ->
                        dockerService.start(spec,
                                outCallback != null ? outCallback::onLog : null,
                                errCallback != null ? errCallback::onLog : null));
    }

    @Override
    public Serializable execute(Variables input) throws Exception {
        TaskParams params = new TaskParams(input);

        boolean logOutput = input.getBoolean(LOG_OUTPUT_KEY, true);

        Result result = delegate
                .storeStdOut(true)
                .logStdOut(logOutput)
                .storeStdErr(true)
                .logStdErr(logOutput)
                .redirectErrorStream(logOutput)
                .execute(params);

        return toMap(result);
    }

    private static HashMap<String, String> toMap(Result result) {
        HashMap<String, String> output = new HashMap<>();
        output.put("stdout", result.getStdOut());
        output.put("stderr", result.getStdErr());
        return output;
    }
}

