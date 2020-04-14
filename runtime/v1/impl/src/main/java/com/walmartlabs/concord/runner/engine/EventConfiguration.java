package com.walmartlabs.concord.runner.engine;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class EventConfiguration {

    private final Collection<String> DEFAULT_IN_VARS_BLACKLIST = Arrays.asList(
            "apiKey",
            "apiToken",
            "password",
            "privateKey",
            "vaultPassword");

    private boolean recordTaskInVars = false;
    private boolean recordTaskOutVars = false;
    private Collection<String> inVarsBlacklist = DEFAULT_IN_VARS_BLACKLIST;
    private Collection<String> outVarsBlacklist = Collections.emptyList();

    public boolean isRecordTaskInVars() {
        return recordTaskInVars;
    }

    public void setRecordTaskInVars(boolean recordTaskInVars) {
        this.recordTaskInVars = recordTaskInVars;
    }

    public boolean isRecordTaskOutVars() {
        return recordTaskOutVars;
    }

    public void setRecordTaskOutVars(boolean recordTaskOutVars) {
        this.recordTaskOutVars = recordTaskOutVars;
    }

    public Collection<String> getInVarsBlacklist() {
        return inVarsBlacklist;
    }

    public void setInVarsBlacklist(Collection<String> inVarsBlacklist) {
        this.inVarsBlacklist = inVarsBlacklist;
    }

    public Collection<String> getOutVarsBlacklist() {
        return outVarsBlacklist;
    }

    public void setOutVarsBlacklist(Collection<String> outVarsBlacklist) {
        this.outVarsBlacklist = outVarsBlacklist;
    }
}
