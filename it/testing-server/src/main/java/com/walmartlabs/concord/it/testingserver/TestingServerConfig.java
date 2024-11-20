package com.walmartlabs.concord.it.testingserver;

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

import com.google.inject.Module;
import com.typesafe.config.Config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record TestingServerConfig(Optional<Integer> apiPort,
                                  Map<String, String> extraConfiguration,
                                  List<Function<Config, Module>> extraModules,
                                  Optional<String> adminApiToken,
                                  Optional<String> agentApiToken) {

    public static TestingServerConfig createDefault() {
        return new TestingServerConfig(Optional.empty(), Map.of(), List.of(), Optional.empty(), Optional.empty());
    }

    public TestingServerConfig withApiPort(int apiPort) {
        return new TestingServerConfig(Optional.of(apiPort), this.extraConfiguration, this.extraModules, this.adminApiToken, this.agentApiToken);
    }

    public TestingServerConfig withOptionalAdminApiKey(Optional<String> adminApiKey) {
        return new TestingServerConfig(this.apiPort, this.extraConfiguration, this.extraModules, adminApiKey, this.agentApiToken);
    }

    public TestingServerConfig withExtraConfiguration(Map<String, String> extraConfiguration) {
        return new TestingServerConfig(this.apiPort, extraConfiguration, this.extraModules, this.adminApiToken, this.agentApiToken);
    }
}
