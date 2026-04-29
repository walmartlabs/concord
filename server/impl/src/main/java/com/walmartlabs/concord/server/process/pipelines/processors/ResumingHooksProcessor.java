package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import java.util.Collections;
import java.util.List;

/**
 * Executes resume hooks.
 * <p/>
 * Resume hooks are used to execute additional logic when the process is resumed.
 * Hooks are typically added during the creation of the {@link Payload}.
 *
 * @see Payload#RESUME_HOOKS
 */
public class ResumingHooksProcessor implements PayloadProcessor {

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        List<Runnable> hooks = payload.getHeader(Payload.RESUME_HOOKS, Collections.emptyList());
        hooks.forEach(Runnable::run);
        return chain.process(payload);
    }
}
