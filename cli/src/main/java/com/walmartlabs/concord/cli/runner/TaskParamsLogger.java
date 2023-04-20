package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallEvent;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskParamsLogger implements TaskCallListener {

    @Override
    public void onEvent(TaskCallEvent event) {
        Map<String, Object> inVars = convertInput(event.input());
        Map<String, Object> outVars = asMapOrNull(event.result());

        if (TaskCallEvent.Phase.PRE.equals(event.phase())) {
            System.out.println("     in: " + inVars);
        } else {
            System.out.println("     out: " + outVars);
            System.out.println("     duration: " + event.duration() + "ms");
            if (event.error() != null) {
                System.out.println("    error: " + event.error());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMapOrNull(Object v) {
        if (v instanceof TaskResult.SimpleResult) {
            return ((TaskResult.SimpleResult) v).toMap();
        }

        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }

        return null;
    }

    private static Map<String, Object> convertInput(List<Object> input) {
        if (input.isEmpty()) {
            return Collections.emptyMap();
        }

        if (input.size() == 1) {
            if (input.get(0) instanceof Variables) {
                return ((Variables) input.get(0)).toMap();
            }
        }

        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < input.size(); i++) {
            Object arg = input.get(i);
            if (arg instanceof Context) {
                arg = "context";
            }
            result.put(String.valueOf(i), arg);
        }

        return result;
    }
}
