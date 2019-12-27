package com.walmartlabs.concord.agent.cfg;

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

import com.typesafe.config.Config;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static com.walmartlabs.concord.agent.cfg.Utils.getDir;
import static com.walmartlabs.concord.agent.cfg.Utils.getStringOrDefault;

@Named
@Singleton
public class RunnerV1Configuration {

    private final Path path;
    private final Path cfgDir;
    private final String javaCmd;
    private final boolean securityManagerEnabled;

    @Inject
    public RunnerV1Configuration(Config cfg) {
        String path = getStringOrDefault(cfg, "runnerV1.path", () -> {
            try {
                Properties props = new Properties();
                props.load(RunnerV1Configuration.class.getResourceAsStream("runnerV1.properties"));
                return props.getProperty("path");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        this.path = Paths.get(path);
        this.cfgDir = getDir(cfg, "runnerV1.cfgDir");
        this.javaCmd = cfg.getString("runnerV1.javaCmd");
        this.securityManagerEnabled = cfg.getBoolean("runnerV1.securityManagerEnabled");
    }

    public Path getPath() {
        return path;
    }

    public Path getCfgDir() {
        return cfgDir;
    }

    public String getJavaCmd() {
        return javaCmd;
    }

    public boolean isSecurityManagerEnabled() {
        return securityManagerEnabled;
    }
}
