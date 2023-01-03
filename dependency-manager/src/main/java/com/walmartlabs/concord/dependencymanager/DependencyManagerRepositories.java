package com.walmartlabs.concord.dependencymanager;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class DependencyManagerRepositories {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

    private static final String CFG_FILE_KEY = "CONCORD_MAVEN_CFG";

    private static final MavenRepository MAVEN_CENTRAL = MavenRepository.builder()
            .id("central")
            .contentType("default")
            .url("https://repo.maven.apache.org/maven2/")
            .snapshotPolicy(MavenRepositoryPolicy.builder()
                    .enabled(false)
                    .build())
            .build();

    private static final List<MavenRepository> DEFAULT_REPOS = Collections.singletonList(MAVEN_CENTRAL);

    public static List<MavenRepository> get() {
        Path src = getConfigFileLocation();
        if (src == null) {
            return DEFAULT_REPOS;
        }
        return readCfg(src);
    }

    public static List<MavenRepository> get(Path cfgFile) {
        return readCfg(cfgFile);
    }

    private static Path getConfigFileLocation() {
        String s = System.getenv(CFG_FILE_KEY);
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return Paths.get(s);
    }

    private static List<MavenRepository> readCfg(Path src) {
        src = src.toAbsolutePath().normalize();

        if (!Files.exists(src)) {
            log.warn("readCfg -> file not found: {}, using the default repos", src);
            return DEFAULT_REPOS;
        }

        ObjectMapper om = new ObjectMapper();
        try (InputStream in = Files.newInputStream(src)) {
            MavenRepositoryConfiguration cfg = om.readValue(in, MavenRepositoryConfiguration.class);
            return cfg.repositories();
        } catch (IOException e) {
            throw new RuntimeException("Error while reading the Maven configuration file: " + src, e);
        }
    }
}
