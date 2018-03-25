package com.walmartlabs.concord.dependencymanager;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@Ignore
public class DependencyManagerTest {

    @Test(timeout = 30000)
    public void test() throws Exception {
        Path tmpDir = Files.createTempDirectory("test");
        URI uriA = new URI("mvn://com.walmartlabs.concord:concord-project-model:0.44.0?scope=runtime");
        URI uriB = new URI("mvn://com.walmartlabs.concord:concord-project-model:0.43.0?scope=runtime");

        DependencyManager m = new DependencyManager(tmpDir);
        Collection<DependencyEntity> paths = m.resolve(Arrays.asList(uriA, uriB));
        assertEquals(10, paths.size());
    }
}
