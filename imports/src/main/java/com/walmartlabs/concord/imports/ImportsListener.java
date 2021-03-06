package com.walmartlabs.concord.imports;

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

import java.util.List;

public interface ImportsListener {

    /**
     * Listener that does nothing.
     */
    ImportsListener NOP_LISTENER = new ImportsListener() {
    };

    default void onStart(List<Import> items) {
    }

    default void onEnd(List<Import> items) {
    }

    default void beforeImport(Import i) {
    }

    default void afterImport(Import i) {
    }
}