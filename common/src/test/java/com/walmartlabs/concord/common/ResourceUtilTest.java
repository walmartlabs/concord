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

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceUtilTest {

    @Test
    public void testParsePattern() {
        var baseDir = Paths.get("/tmp/foo");

        var pattern = ResourceUtils.parsePattern(baseDir, "glob:*.txt");
        assertTrue(pattern.matches(Paths.get("/tmp/foo/bar.txt")));
        assertTrue(pattern.matches(Paths.get("/tmp/foo/bar!.txt")));
        assertFalse(pattern.matches(Paths.get("/tmp/foo/baz.tmp")));

        pattern = ResourceUtils.parsePattern(baseDir, "glob:concord/{**/,}{*.,}concord.{yml,yaml}");
        assertTrue(pattern.matches(Paths.get("/tmp/foo/concord/foo.concord.yml")));
        assertTrue(pattern.matches(Paths.get("/tmp/foo/concord/bar.concord.yaml")));
        assertFalse(pattern.matches(Paths.get("/tmp/foo/qux.concord.yaml")));

        pattern = ResourceUtils.parsePattern(baseDir, "regex:.*\\.qux");
        assertTrue(pattern.matches(Paths.get("/tmp/foo/bar.qux")));
        assertFalse(pattern.matches(Paths.get("/tmp/foo/qux")));

        pattern = ResourceUtils.parsePattern(baseDir, "baz.txt");
        assertTrue(pattern.matches(Paths.get("/tmp/foo/baz.txt")));
        assertFalse(pattern.matches(Paths.get("/tmp/foo/qux.txt")));
    }
}
