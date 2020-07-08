package com.walmartlabs.concord.dependencymanager;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.eclipse.aether.repository.Proxy;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutableMavenProxy.class)
@JsonDeserialize(as = ImmutableMavenProxy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface MavenProxy {

    @Value.Default
    default String type() {
        return Proxy.TYPE_HTTP;
    }

    String host();

    int port();

    static ImmutableMavenProxy.Builder builder() {
        return ImmutableMavenProxy.builder();
    }
}
