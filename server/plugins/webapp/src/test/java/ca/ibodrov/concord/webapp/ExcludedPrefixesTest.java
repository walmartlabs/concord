package ca.ibodrov.concord.webapp;

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

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExcludedPrefixesTest {

    @Test
    public void matchWorksAsIntended() {
        var excludedPrefixes = new ExcludedPrefixes(Stream.of("/", "/api/*", "/foo/*", "/bar"));

        assertTrue(excludedPrefixes.matches("/"));
        assertTrue(excludedPrefixes.matches("/api/foobar"));
        assertTrue(excludedPrefixes.matches("/foo/bar"));
        assertTrue(excludedPrefixes.matches("/bar"));

        assertFalse(excludedPrefixes.matches("/test"));
        assertFalse(excludedPrefixes.matches("/api"));
        assertFalse(excludedPrefixes.matches("/foo"));
        assertFalse(excludedPrefixes.matches("/baz"));
    }
}
