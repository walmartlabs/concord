package com.walmartlabs.concord.plugins.ansible;

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

public class NopAuth implements AnsibleAuth {

    @Override
    public void prepare() {
        // do nothing
    }

    @Override
    public AnsibleAuth enrich(AnsibleEnv env, AnsibleContext context) {
        // do nothing
        return this;
    }

    @Override
    public AnsibleAuth enrich(PlaybookScriptBuilder p) {
        // do nothing
        return this;
    }

    @Override
    public void postProcess() {
        // do nothing
    }
}
