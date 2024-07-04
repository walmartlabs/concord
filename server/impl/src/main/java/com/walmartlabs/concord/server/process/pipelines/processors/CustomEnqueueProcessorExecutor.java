package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.sdk.process.CustomEnqueueProcessor;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.Set;

@Named
public class CustomEnqueueProcessorExecutor implements PayloadProcessor {

    private final Collection<CustomEnqueueProcessor> processors;

    @Inject
    public CustomEnqueueProcessorExecutor(Set<CustomEnqueueProcessor> processors) {
        this.processors = processors;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        for (CustomEnqueueProcessor p : processors) {
            payload = p.process(payload);
        }
        return chain.process(payload);
    }
}
