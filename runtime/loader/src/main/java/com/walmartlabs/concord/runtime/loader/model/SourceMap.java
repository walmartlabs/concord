package com.walmartlabs.concord.runtime.loader.model;

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

import com.fasterxml.jackson.core.JsonLocation;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;

@Value.Immutable
public interface SourceMap extends Serializable {

    long serialVersionUID = 1L;

    static SourceMap from(JsonLocation location) {
        return SourceMap.builder()
                .source(location.sourceDescription())
                .line(location.getLineNr())
                .column(location.getColumnNr())
                .build();
    }

    String source();

    int line();

    int column();

    @Nullable
    String description();

    static ImmutableSourceMap.Builder builder() {
        return ImmutableSourceMap.builder();
    }
}
