package com.walmartlabs.concord.dependencymanager;

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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@Disabled
public class DependencyManagerTest {

    @Test
    public void test() throws Exception {
        assertTimeout(Duration.ofMillis(30000), () -> {
            Path tmpDir = Files.createTempDirectory("test");
            URI uriA = new URI("mvn://com.walmartlabs.concord:concord-policy-engine:1.44.0?scope=runtime");
            URI uriB = new URI("mvn://com.walmartlabs.concord:concord-policy-engine:1.43.0?scope=runtime");

            DependencyManager m = new DependencyManager(DependencyManagerConfiguration.of(tmpDir));
            Collection<DependencyEntity> paths = m.resolve(Arrays.asList(uriA, uriB));
            assertEquals(46, paths.size());
        });
    }

    @Disabled
    @Test
    public void testProxy() {
        assertTimeout(Duration.ofMillis(30000), () -> {
            Path tmpDir = Files.createTempDirectory("test");

            List<MavenRepository> repositories = Collections.singletonList(
                    MavenRepository.builder()
                            .id("test")
                            .url("https://repo.maven.apache.org/maven2/")
                            .proxy(MavenProxy.builder()
                                    .host("localhost")
                                    .port(3128)
                                    .build())
                            .build()
            );

            DependencyManager m = new DependencyManager(DependencyManagerConfiguration.of(tmpDir, repositories));
            m.resolveSingle(new URI("mvn://com.walmartlabs.concord:concord-sdk:1.54.0"));
        });
    }

    @Disabled
    @Test
    public void testOfflineMode() {
        assertTimeout(Duration.ofMillis(30000), () -> {
            Path tmpDir = Files.createTempDirectory("test");

            List<MavenRepository> repositories = Collections.singletonList(
                    MavenRepository.builder()
                            .id("test")
                            .url("https://repo.maven.apache.org/maven2/")
                            .proxy(MavenProxy.builder()
                                    .host("localhost")
                                    .port(3128)
                                    .build())
                            .build()
            );

            DependencyManager m = new DependencyManager(DependencyManagerConfiguration.builder()
                    .cacheDir(tmpDir)
                    .repositories(repositories)
                    .offlineMode(true)
                    .build());
            m.resolveSingle(new URI("mvn://com.walmartlabs.concord:concord-sdk:1.54.0"));
        });
    }
}
