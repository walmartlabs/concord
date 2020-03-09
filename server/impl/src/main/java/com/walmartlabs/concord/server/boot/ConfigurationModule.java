package com.walmartlabs.concord.server.boot;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.google.inject.AbstractModule;
import com.walmartlabs.ollie.config.ConfigurationProcessor;
import com.walmartlabs.ollie.config.Environment;
import com.walmartlabs.ollie.config.EnvironmentSelector;
import com.walmartlabs.ollie.config.OllieConfigurationModule;

public class ConfigurationModule extends AbstractModule {

    @Override
    protected void configure() {
        Environment env = new EnvironmentSelector().select();
        com.typesafe.config.Config config = new ConfigurationProcessor("concord-server", env, null, null).process();
        install(new OllieConfigurationModule("com.walmartlabs.concord.server", config));
    }
}
