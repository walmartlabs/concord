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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.DockerContainerSpec;
import com.walmartlabs.concord.sdk.DockerService;

import java.io.IOException;

public class DockerServiceImpl implements DockerService {

    @Override
    public Process start(Context ctx, DockerContainerSpec spec) throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int start(Context ctx, DockerContainerSpec spec, LogCallback outCallback, LogCallback errCallback) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("not implemented");
    }
}
