package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.ProjectDefinitionUtils;
import com.walmartlabs.concord.runner.engine.el.InjectVariableELResolver;
import com.walmartlabs.concord.runner.engine.el.TaskResolver;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.RpcClient;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.Configuration;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.Engine;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.JavaDelegate;
import io.takari.bpm.context.ExecutionContextFactory;
import io.takari.bpm.context.ExecutionContextImpl;
import io.takari.bpm.el.DefaultExpressionManager;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.event.EventStorage;
import io.takari.bpm.form.*;
import io.takari.bpm.form.DefaultFormService.NoopResumeHandler;
import io.takari.bpm.lock.NoopLockManager;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SourceAwareProcessDefinition;
import io.takari.bpm.persistence.PersistenceManager;
import io.takari.bpm.task.JavaDelegateHandler;
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
    public EngineFactory(NamedTaskRegistry taskRegistry, RpcClient rpcClient) {
        this.taskRegistry = taskRegistry;
        this.rpcClient = rpcClient;
    }

    public Engine create(ProjectDefinition project, Path baseDir, Collection<String> activeProfiles) {
        Path attachmentsDir = baseDir.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME);
        Path stateDir = attachmentsDir.resolve(InternalConstants.Files.JOB_STATE_DIR_NAME);

        Path eventsDir = stateDir.resolve("events");
        Path instancesDir = stateDir.resolve("instances");
        Path formsDir = stateDir.resolve(InternalConstants.Files.JOB_FORMS_DIR_NAME);

        try {
            Files.createDirectories(eventsDir);
            Files.createDirectories(instancesDir);
            Files.createDirectories(formsDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ExpressionManager expressionManager = new DefaultExpressionManager(
                new String[]{InternalConstants.Context.CONTEXT_KEY, InternalConstants.Context.EXECUTION_CONTEXT_KEY},
                new TaskResolver(taskRegistry),
                new InjectVariableELResolver());

        ExecutionContextFactory<? extends ExecutionContextImpl> contextFactory = new ConcordExecutionContextFactory(expressionManager);

        EventStorage eventStorage = new FileEventStorage(eventsDir);
        PersistenceManager persistenceManager = new FilePersistenceManager(instancesDir);

        FormStorage formStorage = new FileFormStorage(formsDir);
        FormService formService = new DefaultFormService(contextFactory, new NoopResumeHandler(), formStorage);

        ProjectDefinitionAdapter adapter = new ProjectDefinitionAdapter(project, activeProfiles, baseDir);

        UserTaskHandler uth = new FormTaskHandler(contextFactory, adapter.forms(), formService);

        Configuration cfg = new Configuration();
        cfg.setInterpolateInputVariables(true);
        cfg.setWrapAllExceptionsAsBpmnErrors(true);
        cfg.setCopyAllCallActivityOutVariables(true);

        Engine result = new EngineBuilder()
                .withContextFactory(contextFactory)
                .withLockManager(new NoopLockManager())
                .withExpressionManager(expressionManager)
                .withDefinitionProvider(adapter.processes())
                .withTaskRegistry(taskRegistry)
                .withJavaDelegateHandler(new JavaDelegateHandlerImpl())
                .withEventStorage(eventStorage)
                .withPersistenceManager(persistenceManager)
                .withUserTaskHandler(uth)
                .withConfiguration(cfg)
                .withListener(new ProcessOutVariablesListener(contextFactory, attachmentsDir))
                .withResourceResolver(new ResourceResolverImpl(baseDir))
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

            // for backward compatibility
            String localPath = baseDir.toAbsolutePath().toString();
            this.attributes = Collections.singletonMap(InternalConstants.Context.LOCAL_PATH_ATTR, localPath);
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

    private static final class JavaDelegateHandlerImpl implements JavaDelegateHandler {

        @Override
        public void execute(Object task, ExecutionContext ctx) throws Exception {
            // check JavaDelegate first because tasks can implement both JavaDelegate and Task
            if (task instanceof JavaDelegate) {
                JavaDelegate d = (JavaDelegate) task;
                d.execute(ctx);
            } else if (task instanceof Task) {
                Task t = (Task) task;
                t.execute((Context) ctx);
            } else {
                throw new ExecutionException("Unsupported task type: " + task + ": tasks must implement either " +
                        Task.class.getName() + " or " + JavaDelegate.class.getName() + " interfaces");
            }
        }
    }

}
