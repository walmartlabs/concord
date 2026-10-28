package com.walmartlabs.concord.plugins.example;

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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named("example")
public class ExampleTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ExampleTask.class);

    private final Context context;

    @Inject
    public ExampleTask(Context context) {
        this.context = context;
    }

    public void hello() {
        log.info("Hello!");
    }

    public void hello(String k) {
        Object o = context.variables().get(k);
        log.info("Hello, {}!", o);
    }

    public void helloButLouder(String name) {
        log.info("Hello, {}!!!", name);
    }

    @Override
    public TaskResult execute(Variables input) {
        log.info("Hello, {}. (from method param)", input.get("myName"));
        return TaskResult.success().value("exampleOutput", "Hello!");
    }

    public void call(String a, String b) {
        log.info("We got {} and {}", a, b);
    }
}
