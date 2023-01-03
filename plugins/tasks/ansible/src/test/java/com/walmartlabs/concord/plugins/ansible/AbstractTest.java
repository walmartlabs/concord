package com.walmartlabs.concord.plugins.ansible;

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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractTest {

    protected void assertFile(String resource, Path actual) throws Exception {
        assertEquals(read(resource), new String(Files.readAllBytes(actual.toAbsolutePath())));
    }

    protected Path tempDir(String prefix) throws Exception {
        return Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), prefix);
    }

    protected String read(String f) throws Exception {
        URI uri = getClass().getResource(f).toURI();
        return new String(Files.readAllBytes(Paths.get(uri)));
    }
}
