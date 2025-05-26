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

import java.util.List;

public abstract class Pipeline extends Chain {

    public Pipeline(List<PayloadProcessor> processors) {
        super(processors.toArray(PayloadProcessor[]::new));
    }

    @Override
    public Payload process(Payload payload) {
        Runnable beforeStart = getBeforeStartHandler();
        if (beforeStart != null) {
            beforeStart.run();
        }

        try {
            return super.process(payload);
        } catch (Exception e) {
            ExceptionProcessor p = getExceptionProcessor();
            if (p != null) {
                p.process(payload, e);
            }
            throw e;
        } finally {
            FinalizerProcessor p = getFinalizerProcessor();
            if (p != null) {
                p.process(payload);
            }

            Runnable afterEnd = getAfterEndHandler();
            if (afterEnd != null) {
                afterEnd.run();
            }
        }
    }

    protected ExceptionProcessor getExceptionProcessor() {
        return null;
    }

    protected FinalizerProcessor getFinalizerProcessor() {
        return null;
    }

    protected Runnable getBeforeStartHandler() {
        return null;
    }

    protected Runnable getAfterEndHandler() {
        return null;
    }
}
