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

import com.fasterxml.jackson.annotation.JsonValue;
import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Named;
import java.io.Serial;
import java.io.Serializable;

@Named("mock")
@DryRunReady
public class MockUtilsTask implements Task {

    public static Any any() {
        return new Any();
    }

    public static class Any implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public boolean equals(Object obj) {
            return true;
        }

        @Override
        @JsonValue
        public String toString() {
            return "any";
        }
    }
}
