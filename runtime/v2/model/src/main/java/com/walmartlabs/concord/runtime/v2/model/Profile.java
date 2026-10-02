package com.walmartlabs.concord.runtime.v2.model;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableProfile.class)
@JsonDeserialize(as = ImmutableProfile.class)
public interface Profile extends Serializable {

    long serialVersionUID = 1L;

    @Value.Default
    default ProcessDefinitionConfiguration configuration() {
        return ProcessDefinitionConfiguration.builder().build();
    }

    @Value.Default
    default Set<String> publicFlows() {
        return Collections.emptySet();
    }

    @Value.Default
    default Map<String, Flow> flows() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Form> forms() {
        return Collections.emptyMap();
    }

    /**
     * Original YAML shape of the {@code configuration} block, used to restrict the overlay
     * to the keys it actually contains. Most attributes of
     * {@link ProcessDefinitionConfiguration} have non-empty defaults, which makes an unset
     * attribute indistinguishable from one set to its default once {@link #configuration()}
     * is serialized.
     * <p/>
     * An empty map overrides nothing. {@code null} means the shape is unknown, and
     * {@link #configuration()} has to be applied as a whole.
     */
    @Nullable
    @JsonIgnore
    Map<String, Serializable> rawConfiguration();

    static ImmutableProfile.Builder builder() {
        return ImmutableProfile.builder();
    }
}
