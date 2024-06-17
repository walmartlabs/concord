package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.svm.State;

import java.io.Serializable;
import java.util.Map;

public final class MockInputUtils {

    public static void storeInput(State state, String inputStoreId, Map<String, Object> input) {
        state.setThreadLocal(state.getRootThreadId(), inputStoreId, (Serializable) input);
    }

    public static Map<String, Object> getInput(State state, String inputStoreId) {
        return state.getThreadLocal(state.getRootThreadId(), inputStoreId);
    }

    private MockInputUtils() {
    }
}
