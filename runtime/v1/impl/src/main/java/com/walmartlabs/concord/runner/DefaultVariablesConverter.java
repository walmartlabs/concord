package com.walmartlabs.concord.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Named
public class DefaultVariablesConverter {

    private final TaskClasses tasks;

    @Inject
    public DefaultVariablesConverter(TaskClasses tasks) {
        this.tasks = tasks;
    }

    public Map<String, Object> convert(Path baseDir, Map<String, Object> processCfg) {
        Set<String> eventNames = StateManager.readResumeEvents(baseDir);
        boolean isResume = eventNames != null && !eventNames.isEmpty();
        if (isResume) {
            // do nothing for resume.
            return processCfg;
        }

        Map<String, Object> v2Defaults = MapUtils.getMap(processCfg, "defaultTaskVariables", Collections.emptyMap());
        Map<String, Object> v1Defaults = new HashMap<>(v2Defaults);

        // v1 version of plugins expects default vars as `tasknameParams`
        // v2 version: only `taskname`
        for (Map.Entry<String, Object> e : v2Defaults.entrySet()) {
            if (!e.getKey().endsWith("Params")) {
                v1Defaults.put(e.getKey() + "Params", e.getValue());
            }
        }

        // remove variables with the same name as task name
        v1Defaults.entrySet().removeIf(e -> tasks.get(e.getKey()) != null);

        Map<String, Object> arguments = MapUtils.getMap(processCfg, Constants.Request.ARGUMENTS_KEY, Collections.emptyMap());

        Map<String, Object> result = new HashMap<>(processCfg);
        result.put(Constants.Request.ARGUMENTS_KEY, ConfigurationUtils.deepMerge(v1Defaults, arguments));
        return result;
    }
}
