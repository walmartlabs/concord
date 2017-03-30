package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.format.WorkflowDefinitionProvider;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.Engine;
import io.takari.bpm.el.DefaultExpressionManager;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.event.EventStorage;
import io.takari.bpm.form.*;
import io.takari.bpm.form.DefaultFormService.NoopResumeHandler;
import io.takari.bpm.persistence.PersistenceManager;
import io.takari.bpm.task.UserTaskHandler;

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

    public Engine create(Path baseDir, WorkflowDefinitionProvider workflows) {
        Path stateDir = baseDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.JOB_STATE_DIR_NAME);

        Path eventsDir = stateDir.resolve("events");
        Path instancesDir = stateDir.resolve("instances");
        Path formsDir = stateDir.resolve(Constants.JOB_FORMS_DIR_NAME);

        try {
            Files.createDirectories(eventsDir);
            Files.createDirectories(instancesDir);
            Files.createDirectories(formsDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ExpressionManager expressionManager = new DefaultExpressionManager(taskRegistry);

        WorkflowProviderAdapter wpa = new WorkflowProviderAdapter(workflows);

        EventStorage eventStorage = new FileEventStorage(eventsDir);
        PersistenceManager persistenceManager = new FilePersistenceManager(instancesDir);

        FormStorage formStorage = new FileFormStorage(formsDir);
        FormService formService = new DefaultFormService(new NoopResumeHandler(), formStorage, expressionManager);

        UserTaskHandler uth = new FormTaskHandler(wpa.forms(), formService);

        Engine e = new EngineBuilder()
                .withExpressionManager(expressionManager)
                .withDefinitionProvider(wpa.processes())
                .withTaskRegistry(taskRegistry)
                .withEventStorage(eventStorage)
                .withPersistenceManager(persistenceManager)
                .withUserTaskHandler(uth)
                .build();

        return e;
    }

    private static class WorkflowProviderAdapter {

        private final WorkflowDefinitionProvider delegate;

        private WorkflowProviderAdapter(WorkflowDefinitionProvider delegate) {
            this.delegate = delegate;
        }

        public ProcessDefinitionProvider processes() {
            return id -> delegate.getProcess(id);
        }

        public FormDefinitionProvider forms() {
            return id -> delegate.getForm(id);
        }
    }
}
