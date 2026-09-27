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
     * The profile's {@code configuration} block as it appears in the source YAML, or
     * {@code null} if this profile didn't come from the parser.
     * <p/>
     * Most attributes of {@link ProcessDefinitionConfiguration} have non-empty default
     * values, so an attribute the author never specified is indistinguishable from an
     * attribute explicitly set to its default value once {@link #configuration()} is
     * serialized. A profile is an overlay and must only override what its author actually
     * wrote, so keep the original shape of the block around to tell those two apart.
     * <p/>
     * An empty map means the author wrote nothing to override. That is different from
     * {@code null}, which means the shape was never recorded and {@link #configuration()}
     * therefore has to be applied as a whole -- dropping it instead would silently lose
     * the profile's configuration. The parser always sets this, so {@code null} only
     * happens for profiles assembled by other means.
     * <p/>
     * Ignored when (de)serializing.
     */
    @Nullable
    @JsonIgnore
    Map<String, Serializable> rawConfiguration();

    static ImmutableProfile.Builder builder() {
        return ImmutableProfile.builder();
    }
}
