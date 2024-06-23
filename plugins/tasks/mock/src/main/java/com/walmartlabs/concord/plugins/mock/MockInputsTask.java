package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.State;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@Named("mockInputs")
public class MockInputsTask implements Task {

    private final Context context;

    @Inject
    public MockInputsTask(Context context) {
        this.context = context;
    }

    public Map<String, Object> get(String inputStoreId) {
        State state = context.execution().state();
        return MockInputUtils.getInput(state, inputStoreId);
    }
}
