package com.walmartlabs.concord.dependencymanager;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface DependencyManagerConfiguration {

    static DependencyManagerConfiguration of(Path cacheDir) {
        return builder()
                .cacheDir(cacheDir)
                .build();
    }

    static DependencyManagerConfiguration of(Path cacheDir, List<MavenRepository> repositories) {
        return builder()
                .cacheDir(cacheDir)
                .repositories(repositories)
                .build();
    }

    Path cacheDir();

    @Value.Default
    default boolean strictRepositories() {
        return false;
    }

    @Value.Default
    default List<MavenRepository> repositories() {
        return DependencyManagerRepositories.get();
    }

    @Value.Default
    default List<String> exclusions() {
        return Collections.emptyList();
    }

    @Value.Default
    default boolean explicitlyResolveV1Client() {
        return false;
    }

    @Value.Default
    default boolean offlineMode() {
        return false;
    }

    static ImmutableDependencyManagerConfiguration.Builder builder() {
        return ImmutableDependencyManagerConfiguration.builder();
    }
}
