package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Constants;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.Engine;
import io.takari.bpm.event.EventStorage;
import io.takari.bpm.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Named
public class EngineFactory {

    private final NamedTaskRegistry taskRegistry;

    @Inject
    public EngineFactory(NamedTaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    public Engine create(Path baseDir, ProcessDefinitionProvider definitionProvider) {
        Path stateDir = baseDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.JOB_STATE_DIR_NAME);

        Path eventsDir = stateDir.resolve("events");
        Path instancesDir = stateDir.resolve("instances");

        try {
            Files.createDirectories(eventsDir);
            Files.createDirectories(instancesDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        EventStorage es = new FileEventStorage(eventsDir);
        PersistenceManager pm = new FilePersistenceManager(instancesDir);

        Engine e = new EngineBuilder()
                .withDefinitionProvider(definitionProvider)
                .withTaskRegistry(taskRegistry)
                .withEventStorage(es)
                .withPersistenceManager(pm)
                .build();

        return e;
    }
}
