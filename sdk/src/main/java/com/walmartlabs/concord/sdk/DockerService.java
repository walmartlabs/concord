package com.walmartlabs.concord.sdk;

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

import java.io.IOException;

public interface DockerService {

    /**
     * Start a new Docker container using the provided specification.
     */
    @Deprecated
    Process start(Context ctx, DockerContainerSpec spec) throws IOException;

    int start(Context ctx, DockerContainerSpec spec,
              LogCallback outCallback, LogCallback errCallback) throws IOException, InterruptedException;

    interface LogCallback {

        void onLog(String line);
    }
}
