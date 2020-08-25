package com.walmartlabs.concord.plugins.ansible.v1;

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

import com.walmartlabs.concord.plugins.ansible.docker.AnsibleDockerService;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.DockerContainerSpec.Options;
import com.walmartlabs.concord.sdk.DockerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnsibleDockerServiceV1 implements AnsibleDockerService {

    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private final Context ctx;
    private final DockerService delegate;

    public AnsibleDockerServiceV1(Context ctx, DockerService delegate) {
        this.ctx = ctx;
        this.delegate = delegate;
    }

    @Override
    public int start(DockerContainerSpec spec) throws Exception {
        return delegate.start(ctx, com.walmartlabs.concord.sdk.DockerContainerSpec.builder()
                .image(spec.image())
                .args(spec.args())
                .debug(spec.debug())
                .forcePull(spec.forcePull())
                .env(spec.env())
                .pullRetryCount(spec.pullRetryCount())
                .pullRetryInterval(spec.pullRetryInterval())
                .workdir("/workspace")
                .options(Options.builder()
                        .hosts(spec.extraDockerHosts())
                        .build())
                .build(), line -> processLog.info("ANSIBLE: {}", line), null);
    }
}
