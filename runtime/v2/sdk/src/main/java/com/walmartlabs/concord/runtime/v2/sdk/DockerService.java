package com.walmartlabs.concord.runtime.v2.sdk;

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

import com.walmartlabs.concord.sdk.DockerContainerSpec;

import java.io.IOException;

public interface DockerService {

    /**
     * Starts a new Docker container using the provided {@code spec}.
     * @param spec the container's specification
     * @param outCallback callback for stdout
     * @param errCallback callback for stderr
     * @return exit code of the `docker run` command
     */
    int start(DockerContainerSpec spec, LogCallback outCallback, LogCallback errCallback) throws IOException, InterruptedException; // TODO throw Exception instead?

    interface LogCallback {

        void onLog(String line);
    }
}
