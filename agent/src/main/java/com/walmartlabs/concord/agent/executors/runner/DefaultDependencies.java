package com.walmartlabs.concord.agent.executors.runner;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class DefaultDependencies {

    private static final Logger log = LoggerFactory.getLogger(DefaultDependencies.class);
    private static final String CFG_KEY = "DEFAULT_DEPS_CFG";

    private final List<URI> dependencies;

    public DefaultDependencies() {
        String path = System.getenv(CFG_KEY);
        if (path != null) {
            try (Stream<String> lines = Files.lines(Paths.get(path))) {
                this.dependencies = lines.filter(s -> !s.isBlank())
                        .map(DefaultDependencies::parseUri)
                        .toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            log.info("init -> using external default dependencies configuration: {}", path);
        } else {
            try (InputStream is = DefaultDependencies.class.getResourceAsStream("default-dependencies")) {
                if (is == null) {
                    throw new RuntimeException("Can't find com/walmartlabs/concord/agent/executors/runner/default-dependencies. " +
                                               "This is most likely a bug or an issue with the local build and/or classpath.");
                }
                this.dependencies = new BufferedReader(new InputStreamReader(is)).lines()
                        .filter(s -> !s.isBlank())
                        .map(DefaultDependencies::parseUri).toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            log.info("init -> using classpath default dependencies configuration");
        }
    }

    public List<URI> getDependencies() {
        return dependencies;
    }

    private static URI parseUri(String s) {
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
