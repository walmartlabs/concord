package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.URI;
import java.util.List;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableDependencyRewriteRule.class)
@JsonDeserialize(as = ImmutableDependencyRewriteRule.class)
public interface DependencyRewriteRule extends Serializable {

    long serialVersionUID = 1L;

    @Nullable
    String msg();

    @Nullable
    String groupId();

    @Nullable
    String artifactId();

    @Nullable
    String fromVersion();

    @Nullable
    String toVersion();

    @Nullable
    URI value();

    @Value.Default
    default List<URI> values() {
        return List.of();
    }

    static ImmutableDependencyRewriteRule.Builder builder() {
        return ImmutableDependencyRewriteRule.builder();
    }
}
