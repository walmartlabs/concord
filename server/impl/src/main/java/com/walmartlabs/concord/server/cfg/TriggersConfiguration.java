package com.walmartlabs.concord.server.cfg;

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

import com.walmartlabs.ollie.config.Config;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

public class TriggersConfiguration {

    @Inject
    @Config("triggers.disableAll")
    private boolean disableAll;

    @Inject
    @Config("triggers.disabled")
    private List<String> disabled;

    @Inject
    @Config("triggers.defaultConditions")
    private Map<String, Object> defaultConditions;

    @Inject
    @Config("triggers.defaultConfiguration")
    private Map<String, Object> defaultConfiguration;

    public boolean isDisableAll() {
        return disableAll;
    }

    public List<String> getDisabled(){
        return disabled;
    }

    public Map<String, Object> getDefaultConditions() {
        return defaultConditions;
    }

    public Map<String, Object> getDefaultConfiguration() {
        return defaultConfiguration;
    }

}
