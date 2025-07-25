package com.walmartlabs.concord.runtime.v2.runner.checkpoints;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2025 Walmart Inc.
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

import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultCheckpointServiceTest {

    @Test
    public void simpleClone() throws Exception {
        record Foo(String value) implements Serializable {
        }
        var input = new Foo("foo");
        var output = DefaultCheckpointService.clone(input, input.getClass().getClassLoader());
        assertEquals(input, output);
    }
}
