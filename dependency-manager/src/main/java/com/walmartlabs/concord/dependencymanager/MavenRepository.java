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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutableMavenRepository.class)
@JsonDeserialize(as = ImmutableMavenRepository.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface MavenRepository {

    String id();

    @Value.Default
    @JsonAlias("layout")
    default String contentType() {
        return "default";
    }

    String url();

    @Nullable
    @Value.Redacted
    Map<String, String> auth();

    @Value.Default
    default MavenRepositoryPolicy snapshotPolicy() {
        return MavenRepositoryPolicy.builder().build();
    }

    @Value.Default
    default MavenRepositoryPolicy releasePolicy() {
        return MavenRepositoryPolicy.builder().build();
    }

    static ImmutableMavenRepository.Builder builder() {
        return ImmutableMavenRepository.builder();
    }
}
