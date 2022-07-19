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

import org.immutables.value.Value;

import java.io.Serializable;

/**
 * @deprecated use {@link com.walmartlabs.concord.runtime.v2.model.Loop}
 */
@Deprecated
@Value.Immutable
@Value.Style(jdkOnly = true)
public interface WithItems extends Serializable {

    long serialVersionUID = 1L;

    static WithItems of(Serializable items, Mode mode) {
        return ImmutableWithItems.of(items, mode);
    }

    @Value.Parameter
    Serializable value();

    @Value.Parameter
    Mode mode();

    enum Mode {
        SERIAL,
        PARALLEL
    }
}
