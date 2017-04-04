package com.walmartlabs.concord.runner;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.format.WorkflowDefinitionProvider;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import com.walmartlabs.concord.runner.engine.NamedTaskRegistry;
import io.takari.bpm.api.Engine;
import io.takari.bpm.model.*;
import io.takari.bpm.model.form.FormDefinition;
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
        ProcessProvider wdp = id -> new ProcessDefinition(id,
                new StartEvent("start"),
                new SequenceFlow("f1", "start", "ev1"),
                new IntermediateCatchEvent("ev1"),
                new SequenceFlow("f2", "ev1", "end"),
                new EndEvent("end"));

        Engine engine = new EngineFactory(taskRegistry).create(baseDir, wdp);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "test", null);

        // ---

        long totalSize = Files.walk(baseDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME))
                .mapToLong(SuspendTest::size).sum();

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

    private interface ProcessProvider extends WorkflowDefinitionProvider {

        @Override
        default FormDefinition getForm(String id) {
            return null;
        }
    }
}
