package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.server.process.Payload;

public class Chain {

    private final PayloadProcessor[] processors;
    private final int current;

    public Chain(PayloadProcessor... processors) {
        this(processors, 0);
    }

    private Chain(PayloadProcessor[] processors, int current) {
        this.processors = processors;
        this.current = current;
    }

    public Payload process(Payload payload) {
        if (current >= processors.length) {
            return payload;
        }

        PayloadProcessor p = processors[current];
        Chain next = new Chain(processors, current + 1);

        return p.process(next, payload);
    }
}
