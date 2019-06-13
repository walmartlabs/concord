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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.DockerContainerSpec;
import com.walmartlabs.concord.sdk.DockerService;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DockerPlaybookProcessBuilder implements PlaybookProcessBuilder {

    private final DockerService dockerService;
    private final Context ctx;
    private final String image;

    private Collection<String> hosts;
    private boolean debug = false;
    private boolean forcePull = true;

    public DockerPlaybookProcessBuilder(DockerService dockerService, Context ctx, String image) {
        this.dockerService = dockerService;
        this.ctx = ctx;
        this.image = image;
    }

    @Override
    public PlaybookProcessBuilder withDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public DockerPlaybookProcessBuilder withForcePull(boolean forcePull) {
        this.forcePull = forcePull;
        return this;
    }

    public DockerPlaybookProcessBuilder withHosts(Collection<String> hosts) {
        this.hosts = hosts;
        return this;
    }

    @Override
    public Process build(List<String> args, Map<String, String> extraEnv) throws IOException {
        return dockerService.start(ctx, DockerContainerSpec.builder()
                .image(image)
                .args(args)
                .env(extraEnv)
                .debug(debug)
                .forcePull(forcePull)
                .options(DockerContainerSpec.Options.builder().hosts(hosts).build())
                .workdir("/workspace") // TODO constants? move into the docker service as a default workdir value?
                .build());
    }
}
