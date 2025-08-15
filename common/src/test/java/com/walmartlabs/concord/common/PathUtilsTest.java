package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PathUtilsTest {

    @Test
    public void testResolveNonChild() {
        Path parent = Paths.get("/parent/path");
        Exception e = assertThrows(IOException.class, () -> PathUtils.assertInPath(parent, "../child"));
        assertTrue(e.getMessage().contains("Child path resolves outside of parent path"));
    }

    @Test
    public void testResolveValidChild() {
        Path parent = Paths.get("/parent/path");
        assertDoesNotThrow(() -> PathUtils.assertInPath(parent, "child"));
        assertDoesNotThrow(() -> PathUtils.assertInPath(parent, "another/child"));

        Path p = assertDoesNotThrow(() -> PathUtils.assertInPath(parent, "odd/../but/valid"));
        assertEquals("/parent/path/but/valid", p.toString());
    }
}
