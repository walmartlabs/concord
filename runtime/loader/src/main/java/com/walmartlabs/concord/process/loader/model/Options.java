package com.walmartlabs.concord.process.loader.model;

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

import com.walmartlabs.concord.common.AllowNulls;
import org.immutables.value.Value;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value.Immutable
public interface Options {

    @Value.Default
    @AllowNulls
    default Map<String, Object> configuration() {
        return Collections.emptyMap();
    }

    @Value.Default
    default List<String> activeProfiles() {
        return Collections.emptyList();
    }

    static ImmutableOptions.Builder builder() {
        return ImmutableOptions.builder();
    }
}
