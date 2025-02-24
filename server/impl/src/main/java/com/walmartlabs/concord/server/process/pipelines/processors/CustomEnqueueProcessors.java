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

import com.walmartlabs.concord.server.sdk.process.CustomEnqueueProcessor;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Named
public class CustomEnqueueProcessors {

    private final Collection<CustomEnqueueProcessor> processors;

    @Inject
    public CustomEnqueueProcessors(Set<CustomEnqueueProcessor> processors) {
        this.processors = List.copyOf(processors);
    }

    public PayloadProcessor handleAttachments() {
        return (chain, payload) -> {
            for (CustomEnqueueProcessor p : processors) {
                payload = p.handleAttachments(payload);
            }
            return chain.process(payload);
        };
    }

    public PayloadProcessor handleState() {
        return (chain, payload) -> {
            for (CustomEnqueueProcessor p : processors) {
                payload = p.handleState(payload);
            }
            return chain.process(payload);
        };
    }

    public PayloadProcessor handleConfiguration() {
        return (chain, payload) -> {
            for (CustomEnqueueProcessor p : processors) {
                payload = p.handleConfiguration(payload);
            }
            return chain.process(payload);
        };
    }
}
