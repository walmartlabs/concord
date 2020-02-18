package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.Serializable;
import java.util.Map;

@Named("log")
public class LogTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(LogTask.class);

    @Override
    public Serializable execute(TaskContext ctx) {
        Map<String, Object> input = ctx.input();
        Object msg = input.get("msg");
        if (msg == null) {
            // TODO check the task's call style
            msg = input.get("0");
        }

        log.info("{}", msg);
        return null;
    }
}
