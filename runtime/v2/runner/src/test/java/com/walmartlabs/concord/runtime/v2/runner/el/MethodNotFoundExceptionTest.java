package com.walmartlabs.concord.runtime.v2.runner.el;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodNotFoundExceptionTest {

    @Test
    public void distanceHandlesEditsAndUtf16() {
        assertEquals(0, MethodNotFoundException.distance("", ""));
        assertEquals(3, MethodNotFoundException.distance("", "abc"));
        assertEquals(0, MethodNotFoundException.distance("same", "same"));
        assertEquals(1, MethodNotFoundException.distance("cat", "cats"));
        assertEquals(1, MethodNotFoundException.distance("cats", "cat"));
        assertEquals(1, MethodNotFoundException.distance("cat", "cut"));
        assertEquals(3, MethodNotFoundException.distance("kitten", "sitting"));
        assertEquals(3, MethodNotFoundException.distance("sitting", "kitten"));
        assertEquals(2, MethodNotFoundException.distance("", "\uD83D\uDE00"));
        assertEquals(1, MethodNotFoundException.distance("\uD83D\uDE00", "\uD83D\uDE01"));
    }

    @Test
    public void hintsAreRankedAndLimitedToPublicMethods() {
        MethodNotFoundException e = new MethodNotFoundException(TestBean.class, "read", null);
        String message = e.getMessage();

        int first = message.indexOf("read()");
        int second = message.indexOf("reads()");
        int third = message.indexOf("readers()");
        assertTrue(first >= 0 && first < second && second < third);
        assertFalse(message.contains("unrelated()"));
        assertFalse(message.contains("readx()"));
    }

    public static class TestBean {
        public void read() {
        }

        public void reads() {
        }

        public void readers() {
        }

        public void unrelated() {
        }

        private void readx() {
        }
    }
}
