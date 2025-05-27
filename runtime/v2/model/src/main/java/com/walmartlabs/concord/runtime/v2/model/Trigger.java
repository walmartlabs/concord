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

import com.walmartlabs.concord.runtime.model.Location;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface Trigger extends Serializable {

    long serialVersionUID = 1L;

    String name();

    @Value.Default
    default Map<String, Object> arguments() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Object> conditions() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Object> configuration() {
        return Collections.emptyMap();
    }

    @Value.Default
    default List<String> activeProfiles() {
        return Collections.emptyList();
    }

    @Value.Default
    default Location location() {
        return Location.builder().build();
    }

    static ImmutableTrigger.Builder builder() {
        return ImmutableTrigger.builder();
    }
}
