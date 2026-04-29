package com.walmartlabs.concord.runtime.v2.runner.tasks;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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


import com.walmartlabs.concord.runtime.v2.sdk.ReentrantTask;
import com.walmartlabs.concord.runtime.v2.sdk.ResumeEvent;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Named("reentrantTask")
@SuppressWarnings("unused")
public class ReentrantTaskExample implements ReentrantTask {

    private static final Logger log = LoggerFactory.getLogger(ReentrantTaskExample.class);

    public static String EVENT_NAME = UUID.randomUUID().toString();

    @Override
    public TaskResult execute(Variables input) {
        log.info("execute {}", input.toMap());

        HashMap<String, Serializable> payload = new HashMap<>();
        payload.put("k", "v");
        payload.put("action", input.assertString("action"));
        payload.put("errorOnResume", input.getBoolean("errorOnResume", false));

        return TaskResult.reentrantSuspend(EVENT_NAME, payload);
    }

    @Override
    public TaskResult resume(ResumeEvent event) {
        log.info("RESUME: {}", event);
        if ((boolean) event.state().get("errorOnResume")) {
            throw new RuntimeException("Error on resume!");
        }

        return TaskResult.success()
                .values((Map) event.state());
    }
}
