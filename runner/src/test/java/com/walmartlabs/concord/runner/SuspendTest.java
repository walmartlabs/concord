package com.walmartlabs.concord.runner;

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

public class SuspendTest {

    @Test
    public void test() throws Exception {
        Path baseDir = Files.createTempDirectory("test");

        NamedTaskRegistry taskRegistry = new NamedTaskRegistry();
        ProjectDefinition project = project(new ProcessDefinition("test",
                new StartEvent("start"),
                new SequenceFlow("f1", "start", "ev1"),
                new IntermediateCatchEvent("ev1"),
                new SequenceFlow("f2", "ev1", "end"),
                new EndEvent("end")));

        Engine engine = new EngineFactory(taskRegistry).create(project, baseDir, Collections.emptySet());

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
        return new ProjectDefinition(Collections.singletonMap(pd.getId(), pd), null, null, null);
    }
}
