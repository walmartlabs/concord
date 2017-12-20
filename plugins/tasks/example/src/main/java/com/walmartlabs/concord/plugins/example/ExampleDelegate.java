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

import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.JavaDelegate;

import javax.inject.Named;

@Named("exampleDelegate")
@Deprecated
public class ExampleDelegate implements Task, JavaDelegate {

    @Override
    public void execute(ExecutionContext ctx) throws Exception {
        ctx.setVariable("exampleOutput", "Hello!");
    }
}
