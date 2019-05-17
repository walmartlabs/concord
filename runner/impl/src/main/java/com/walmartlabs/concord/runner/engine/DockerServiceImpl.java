package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.common.DockerProcessBuilder;
import com.walmartlabs.concord.runner.model.RunnerConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.DockerContainerSpec;
import com.walmartlabs.concord.sdk.DockerService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Named
@Singleton
public class DockerServiceImpl implements DockerService {

    private static final String WORKSPACE_TARGET_DIR = "/workspace";

    private final List<String> extraVolumes;

    @Inject
    public DockerServiceImpl(RunnerConfiguration runnerCfg) {
        this.extraVolumes = runnerCfg.docker().extraVolumes();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Process start(Context ctx, DockerContainerSpec spec) throws IOException {
        DockerProcessBuilder b = DockerProcessBuilder.from(ctx, spec);

        List<String> volumes = new ArrayList<>();
        // add the default volume - mount the process' workDir as /workspace
        volumes.add("${" + Constants.Context.WORK_DIR_KEY + "}:" + WORKSPACE_TARGET_DIR);
        // add extra volumes from the runner's arguments
        volumes.addAll(extraVolumes);

        b.volumes((Collection<String>) ctx.interpolate(volumes));

        return b.build();
    }
}
