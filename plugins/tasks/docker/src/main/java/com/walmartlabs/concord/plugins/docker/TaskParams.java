package com.walmartlabs.concord.plugins.docker;

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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Collection;
import java.util.Map;

import static com.walmartlabs.concord.plugins.docker.DockerConstants.*;

public class TaskParams {

    private final Variables variables;

    public TaskParams(Map<String, Object> in) {
        this(new MapBackedVariables(in));
    }

    public TaskParams(Variables variables) {
        this.variables = variables;
    }

    public String image() {
        return variables.assertString(IMAGE_KEY);
    }

    public String cmd() {
        return variables.getString(CMD_KEY, null);
    }

    public Map<String, Object> env() {
        return variables.getMap(ENV_KEY, null);
    }

    public String envFile() {
        return variables.getString(ENV_FILE_KEY, null);
    }

    public Collection<String> hosts() {
        return variables.getCollection(HOSTS_KEY, null);
    }

    public boolean forcePull() {
        return variables.getBoolean(FORCE_PULL_KEY, true);
    }

    public boolean debug(boolean defaultValue) {
        return variables.getBoolean(DEBUG_KEY, defaultValue);
    }

    public int pullRetryCount(){
        return variables.getInt(PULL_RETRY_COUNT_KEY, 3);
    }

    public long pullRetryInterval() {
        return variables.getLong(PULL_RETRY_INTERVAL_KEY, 10_000L);
    }
}
