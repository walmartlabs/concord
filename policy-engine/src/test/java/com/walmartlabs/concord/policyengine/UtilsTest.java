package com.walmartlabs.concord.policyengine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class UtilsTest {

    @Test
    public void testMatchAny() {
        boolean result = Utils.matchAny(Collections.singletonList("\\.concord"), ".concord");
        assertTrue(result);

        result = Utils.matchAny(Collections.emptyList(), ".concord");
        assertFalse(result);
    }

    @Test
    public void testSimple() {
        String s = "100KB";

        long result = Utils.parseFileSize(s);

        assertEquals(100 * 1024, result);
    }

    @Test
    public void testTrim() {
        String s = "   100  kb ";

        long result = Utils.parseFileSize(s);

        assertEquals(100 * 1024, result);
    }
}
