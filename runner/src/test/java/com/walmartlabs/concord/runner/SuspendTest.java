package com.walmartlabs.concord.runner;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import com.walmartlabs.concord.runner.engine.NamedTaskRegistry;
import io.takari.bpm.api.Engine;
import io.takari.bpm.model.*;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SuspendTest {

    @Test
    public void test() throws Exception {
        Path baseDir = Files.createTempDirectory("test");
        Injector injector = mock(Injector.class);
        NamedTaskRegistry taskRegistry = new NamedTaskRegistry(injector, null);
        ProjectDefinition project = project(new ProcessDefinition("test",
                new StartEvent("start"),
                new SequenceFlow("f1", "start", "ev1"),
                new IntermediateCatchEvent("ev1"),
                new SequenceFlow("f2", "ev1", "end"),
                new EndEvent("end")));

        Engine engine = new EngineFactory(mock(ApiClient.class), taskRegistry).create(project, baseDir, Collections.emptySet());

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "test", null);

        // ---

        long totalSize = Files.walk(baseDir).mapToLong(SuspendTest::size).sum();
        assertTrue(totalSize > 0);

        // ---

        engine.resume(key, "ev1", null);
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ProjectDefinition project(ProcessDefinition pd) {
        return new ProjectDefinition(Collections.singletonMap(pd.getId(), pd), null, null, null, null);
    }
}
