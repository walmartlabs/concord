package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.ProjectDefinitionUtils;
import io.takari.bpm.Configuration;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.Engine;
import io.takari.bpm.el.DefaultExpressionManager;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.event.EventStorage;
import io.takari.bpm.form.*;
import io.takari.bpm.form.DefaultFormService.NoopResumeHandler;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SourceAwareProcessDefinition;
import io.takari.bpm.persistence.PersistenceManager;
import io.takari.bpm.resource.ResourceResolver;
import io.takari.bpm.task.UserTaskHandler;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Named
public class EngineFactory {

    private final NamedTaskRegistry taskRegistry;

    private final RpcClient rpcClient;

    @Inject
    public EngineFactory(
            NamedTaskRegistry taskRegistry,
            RpcClient rpcClient) {
        this.taskRegistry = taskRegistry;
        this.rpcClient = rpcClient;
    }

    public Engine create(ProjectDefinition project, Path baseDir, Collection<String> activeProfiles) {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        Path eventsDir = stateDir.resolve("events");
        Path instancesDir = stateDir.resolve("instances");
        Path formsDir = stateDir.resolve(Constants.Files.JOB_FORMS_DIR_NAME);

        try {
            Files.createDirectories(eventsDir);
            Files.createDirectories(instancesDir);
            Files.createDirectories(formsDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ExpressionManager expressionManager = new DefaultExpressionManager(taskRegistry);

        EventStorage eventStorage = new FileEventStorage(eventsDir);
        PersistenceManager persistenceManager = new FilePersistenceManager(instancesDir);

        FormStorage formStorage = new FileFormStorage(formsDir);
        FormService formService = new DefaultFormService(new NoopResumeHandler(), formStorage, expressionManager);

        ProjectDefinitionAdapter adapter = new ProjectDefinitionAdapter(project, activeProfiles, baseDir);

        UserTaskHandler uth = new FormTaskHandler(adapter.forms(), formService, expressionManager);

        ResourceResolver resourceResolver = name -> {
            Path p = baseDir.resolve(name);
            return Files.newInputStream(p);
        };

        Configuration cfg = new Configuration();
        cfg.setInterpolateInputVariables(true);
        cfg.setWrapAllExceptionsAsBpmnErrors(true);

        Engine result = new EngineBuilder()
                .withExpressionManager(expressionManager)
                .withDefinitionProvider(adapter.processes())
                .withTaskRegistry(taskRegistry)
                .withEventStorage(eventStorage)
                .withPersistenceManager(persistenceManager)
                .withUserTaskHandler(uth)
                .withResourceResolver(resourceResolver)
                .withConfiguration(cfg)
                .build();

        result.addInterceptor(new ProcessElementInterceptor(rpcClient, adapter.processes()));

        return result;
    }

    private static class ProjectDefinitionAdapter {

        private final ProjectDefinition project;
        private final Collection<String> activeProfiles;
        private final Map<String, String> attributes;

        private ProjectDefinitionAdapter(ProjectDefinition project, Collection<String> activeProfiles, Path baseDir) {
            this.project = project;
            this.activeProfiles = activeProfiles;

            // TODO this should be replaces with a project attribute
            String localPath = baseDir.toAbsolutePath().toString();
            this.attributes = Collections.singletonMap(Constants.Context.LOCAL_PATH_ATTR, localPath);
        }

        ProcessDefinitionProvider processes() {
            return id -> {
                ProcessDefinition pd = ProjectDefinitionUtils.getFlow(project, activeProfiles, id);
                if (pd == null) {
                    return null;
                }

                return mergeAttr(pd, attributes);
            };
        }

        FormDefinitionProvider forms() {
            return id -> ProjectDefinitionUtils.getForm(project, activeProfiles, id);
        }

        private static ProcessDefinition mergeAttr(ProcessDefinition pd, Map<String, String> attr) {
            Map<String, String> current = pd.getAttributes();
            if (current == null) {
                current = Collections.emptyMap();
            }

            Map<String, String> m = new HashMap<>(current);
            m.putAll(attr);

            if(pd instanceof SourceAwareProcessDefinition) {
                SourceAwareProcessDefinition spd = (SourceAwareProcessDefinition) pd;
                return new SourceAwareProcessDefinition(pd.getId(), pd.getChildren(), m, spd.getSourceMaps());
            } else {
                return new ProcessDefinition(pd, m);
            }
        }
    }
}
