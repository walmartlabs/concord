package com.walmartlabs.concord.cli.runner;

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


import com.walmartlabs.concord.runtime.v2.sdk.DockerContainerSpec;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;

public class CliDockerService implements DockerService {

    @Override
    public int start(DockerContainerSpec spec, LogCallback outCallback, LogCallback errCallback) {
        throw new UnsupportedOperationException("Running Docker containers is not supported by the concord-cli yet");
    }
}
