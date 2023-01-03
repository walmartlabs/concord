package com.walmartlabs.concord.db;

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

import com.walmartlabs.concord.server.sdk.Range;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PgIntRangeTest {

    @Test
    public void test() throws Exception {
        String s = "[123,234)";
        Range r = PgIntRange.parse(s);
        assertEquals(Range.Mode.INCLUSIVE, r.lowerMode());
        assertEquals(123, r.lower());
        assertEquals(234, r.upper());
        assertEquals(Range.Mode.EXCLUSIVE, r.upperMode());
    }
}
