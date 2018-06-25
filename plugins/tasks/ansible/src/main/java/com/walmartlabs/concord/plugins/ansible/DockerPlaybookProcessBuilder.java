package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DockerPlaybookProcessBuilder implements PlaybookProcessBuilder {

    private static final String VOLUME_CONTAINER_DEST = "/workspace";

    private final String txId;
    private final String imageName;
    private final String workdir;

    private List<Map.Entry<String, String>> dockerOpts = Collections.emptyList();

    private boolean debug = false;
    private boolean forcePull = true;

    public DockerPlaybookProcessBuilder(String txId, String imageName, String workdir) {
        this.txId = txId;
        this.imageName = imageName;
        this.workdir = workdir;
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

    public DockerPlaybookProcessBuilder withDockerOptions(List<Map.Entry<String, String>> dockerOpts) {
        this.dockerOpts = dockerOpts;
        return this;
    }

    @Override
    public Process build(List<String> args, Map<String, String> extraEnv) throws IOException {
        return new DockerProcessBuilder(imageName)
                .addLabel(DockerProcessBuilder.CONCORD_TX_ID_LABEL, txId)
                .cleanup(true)
                .env(extraEnv)
                .volume(workdir, VOLUME_CONTAINER_DEST)
                .workdir(VOLUME_CONTAINER_DEST)
                .args(args)
                .options(dockerOpts)
                .debug(debug)
                .forcePull(forcePull)
                .build();
    }
}
