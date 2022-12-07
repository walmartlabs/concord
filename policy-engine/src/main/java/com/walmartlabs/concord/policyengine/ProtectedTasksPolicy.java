package com.walmartlabs.concord.policyengine;

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

public class ProtectedTasksPolicy {

    private final ProtectedTasksRule rule;

    public ProtectedTasksPolicy(ProtectedTasksRule rule) {
        this.rule = rule;
    }

    public boolean isProtected(String taskName) {
        if (rule == null) {
            return false;
        }

        return rule.names().contains(taskName);
    }
}
