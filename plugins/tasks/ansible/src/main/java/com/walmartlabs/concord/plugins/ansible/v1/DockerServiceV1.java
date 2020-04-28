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

import com.walmartlabs.concord.plugins.ansible.AnsibleDockerService;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.DockerContainerSpec;

public class DockerServiceV1 implements AnsibleDockerService {

    private final com.walmartlabs.concord.sdk.DockerService delegate;
    private final Context context;

    public DockerServiceV1(com.walmartlabs.concord.sdk.DockerService delegate, Context context) {
        this.delegate = delegate;
        this.context = context;
    }

    @Override
    public int start(DockerContainerSpec spec, DockerService.LogCallback outCallback, DockerService.LogCallback errCallback) throws Exception {
        com.walmartlabs.concord.sdk.DockerService.LogCallback out = null;
        if (outCallback != null) {
            out = outCallback::onLog;
        }

        com.walmartlabs.concord.sdk.DockerService.LogCallback err = null;
        if (errCallback != null) {
            err = errCallback::onLog;
        }

        return delegate.start(context, spec, out, err);
    }
}
