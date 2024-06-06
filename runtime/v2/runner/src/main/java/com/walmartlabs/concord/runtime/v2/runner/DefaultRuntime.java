package com.walmartlabs.concord.runtime.v2.runner;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.runtime.v2.runner.vm.LoggedException;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.VM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultRuntime implements Runtime {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuntime.class);

    private final VM vm;
    private final Injector injector;
    private final ExecutorService executor;

    public DefaultRuntime(VM vm, Injector injector) {
        this.vm = vm;
        this.injector = injector;

        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public void spawn(State state, ThreadId threadId) {
        executor.submit(() -> {
            try {
                vm.eval(this, state, threadId);
            } catch (LoggedException e) {
                throw e.getCause();
            } catch (Exception e) {
                log.error("Error while evaluating commands for thread {}", threadId, e);
                throw e;
            }
            return null;
        });
    }

    @Override
    public <T> T getService(Class<T> klass) {
        return injector.getInstance(klass);
    }
}
