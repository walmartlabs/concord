package com.walmartlabs.concord.agent.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.walmartlabs.concord.agent.cfg.Utils.*;
import static java.util.stream.Collectors.joining;

public class RuntimeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfiguration.class);

    private final Map<String, Entry> configs;

    @Inject
    public RuntimeConfiguration(Config config) {
        var runtimes = config.getObject("runtimes");
        var configs = new HashMap<String, Entry>();
        for (var runtime : runtimes.keySet()) {
            var cfg = Entry.parse(config.getConfig("runtimes." + runtime));
            configs.put(runtime, cfg);
        }
        log.info("Available runtimes: {}", configs.keySet().stream().sorted().collect(joining(", ")));
        this.configs = Collections.unmodifiableMap(configs);
    }

    public Optional<Entry> getForRuntime(String runtime) {
        var cfg = configs.get(runtime);
        return Optional.ofNullable(cfg);
    }

    public record Entry(Path path,
                        Path cfgDir,
                        String javaCmd,
                        List<String> jvmParams,
                        String mainClass,
                        Path persistentWorkDir,
                        boolean cleanRunnerDescendants,
                        boolean segmentedLogs) {

        public static Entry parse(Config cfg) {
            var pathString = getStringOrDefault(cfg, "path", () -> {
                // support local development, use .properties files to get JAR paths
                // the .properties files are populated during build

                if (!cfg.hasPath("propertiesFile")) {
                    throw new IllegalStateException(".path or .propertiesFile are required");
                }
                var fallback = cfg.getString("propertiesFile");

                var props = new Properties();
                try (var in = Entry.class.getResourceAsStream(fallback)) {
                    if (in == null) {
                        throw new IllegalStateException("Resource not found: " + fallback);
                    }
                    props.load(in);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                return props.getProperty("path");
            });
            var path = Paths.get(pathString);
            var cfgDir = getOrCreatePath(cfg, "cfgDir");
            var javaCmd = getJavaCmd(cfg);
            var jvmParams = cfg.getStringList("jvmParams");
            var mainClass = cfg.getString("mainClass");
            var persistentWorkDir = getOptionalAbsolutePath(cfg, "persistentWorkDir");
            var cleanRunnerDescendants = cfg.getBoolean("cleanRunnerDescendants");
            var segmentedLogs = cfg.getBoolean("segmentedLogs");
            return new Entry(path, cfgDir, javaCmd, jvmParams, mainClass, persistentWorkDir, cleanRunnerDescendants, segmentedLogs);
        }

        private static String getJavaCmd(Config cfg) {
            var path = "javaCmd";

            if (cfg.hasPath(path)) {
                var s = cfg.getString(path);
                if (s != null) {
                    return s;
                }
            }

            var javaHome = System.getProperty("java.home");
            if (javaHome != null) {
                return javaHome + "/bin/java";
            }

            return "java";
        }
    }
}
