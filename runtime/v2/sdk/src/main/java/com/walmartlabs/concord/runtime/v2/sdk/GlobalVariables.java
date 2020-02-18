package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.io.Serializable;
import java.util.Map;

/**
 * Provides access to global variables of the process.
 * All implementations must be serializable and thread safe.
 */
public interface GlobalVariables extends Serializable {

    Object get(String key);

    void put(String key, Object value);

    void putAll(Map<String, Object> values);

    Object remove(String key);

    boolean containsKey(String key);

    Map<String, Object> toMap();
}
