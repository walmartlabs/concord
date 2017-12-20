package com.walmartlabs.concord.plugins.example;

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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named("example")
public class ExampleTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ExampleTask.class);

    @InjectVariable("context")
    private Context context;

    public void hello() {
        log.info("Hello!");
    }

    public void hello(String k) {
        Object o = context.getVariable(k);
        log.info("Hello, {}!", o);
    }

    public void helloButLouder(@InjectVariable("myName") String name) {
        log.info("Hello, {}!!!", name);
    }

    @Override
    public void execute(Context ctx) throws Exception {
        log.info("Hello, {}. (from method param)", ctx.getVariable("myName"));
        log.info("Hello, {}. (from injected var)", context.getVariable("myName"));
        ctx.setVariable("exampleOutput", "Hello!");
    }

    public void call(String a, String b) {
        log.info("We got {} and {}", a, b);
    }
}
