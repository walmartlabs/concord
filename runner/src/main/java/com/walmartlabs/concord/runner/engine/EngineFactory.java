package com.walmartlabs.concord.runner.engine;

import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.Engine;
import io.takari.bpm.event.EventPersistenceManager;
import io.takari.bpm.event.EventPersistenceManagerImpl;
import io.takari.bpm.event.InMemEventStorage;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class EngineFactory {

    private final NamedTaskRegistry taskRegistry;

    @Inject
    public EngineFactory(NamedTaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    public Engine create(ProcessDefinitionProvider definitionProvider) {
        EventPersistenceManager epm = new EventPersistenceManagerImpl(new InMemEventStorage());

        Engine e = new EngineBuilder()
                .withDefinitionProvider(definitionProvider)
                .withTaskRegistry(taskRegistry)
                .withEventManager(epm)
                .build();

        return e;
    }
}
