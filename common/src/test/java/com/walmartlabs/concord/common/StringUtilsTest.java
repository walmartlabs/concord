package com.walmartlabs.concord.common;

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

import org.junit.Test;

import static org.junit.Assert.*;

public class StringUtilsTest {

    @Test
    public void test() {
        try {
            assertEquals("123", StringUtils.abbreviate("123", 3));
            fail("exception expected");
        } catch (Exception e) {
            // expected
        }

        assertNull(StringUtils.abbreviate(null, 5));

        assertEquals("1234", StringUtils.abbreviate("1234", 5));

        assertEquals("12345", StringUtils.abbreviate("12345", 5));

        assertEquals("12...", StringUtils.abbreviate("123456", 5));
    }
}
