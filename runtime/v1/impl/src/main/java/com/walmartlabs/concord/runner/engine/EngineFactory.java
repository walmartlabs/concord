package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.common.ReflectionUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.ProjectDefinitionUtils;
import com.walmartlabs.concord.runner.PolicyEngineHolder;
import com.walmartlabs.concord.runner.VariablesSnapshotListener;
import com.walmartlabs.concord.runner.engine.el.InjectVariableELResolver;
import com.walmartlabs.concord.runner.engine.el.TaskResolver;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.Configuration;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.Engine;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.JavaDelegate;
import io.takari.bpm.context.DefaultExecutionContextFactory;
import io.takari.bpm.el.DefaultExpressionManager;
import io.takari.bpm.el.ExecutionContextVariableResolver;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.event.EventStorage;
import io.takari.bpm.form.DefaultFormService.NoopResumeHandler;
import io.takari.bpm.form.FormDefinitionProvider;
import io.takari.bpm.form.FormService;
import io.takari.bpm.form.FormStorage;
import io.takari.bpm.form.FormTaskHandler;
import io.takari.bpm.lock.NoopLockManager;
import io.takari.bpm.persistence.PersistenceManager;
import io.takari.bpm.task.JavaDelegateHandler;
import io.takari.bpm.task.ServiceTaskRegistry;
import io.takari.bpm.task.UserTaskHandler;

import javax.el.CompositeELResolver;
import javax.el.ELResolver;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Named
public class EngineFactory {

    private final ApiClientFactory apiClientFactory;
    private final ServiceTaskRegistry taskRegistry;

    @Inject
    public EngineFactory(ApiClientFactory apiClientFactory,
                         ServiceTaskRegistry taskRegistry) {

        this.apiClientFactory = apiClientFactory;
        this.taskRegistry = taskRegistry;
    }

    @SuppressWarnings("deprecation")
    public Engine create(ProjectDefinition project,
                         Path baseDir,
                         Collection<String> activeProfiles,
                         Set<String> metaVariables,
                         EventConfiguration eventCfg) {

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
                new InjectVariableELResolver()) {

            @Override
            protected ELResolver createResolver(ExecutionContext ctx) {
                CompositeELResolver cr = new CompositeELResolver();
                cr.add(new ExecutionContextVariableResolver(ctx));
                for (ELResolver r : resolvers) {
                    cr.add(r);
                }
                return cr;
            }
        };

        FormStorage formStorage = new FileFormStorage(formsDir);
        // here we create a separate ContextFactory to avoid circular references
        // between ConcordFormService and ConcordExecutionContextFactory
        // TODO find a better way
        FormService formService = new ConcordFormService(new DefaultExecutionContextFactory(expressionManager), new NoopResumeHandler(), formStorage);

        ProtectedVarContext protectedVarContext = new ProtectedVarContext(PolicyEngineHolder.INSTANCE.getEngine());
        ConcordExecutionContextFactory contextFactory = new ConcordExecutionContextFactory(expressionManager, protectedVarContext, formService);

        EventStorage eventStorage = new FileEventStorage(eventsDir);
        PersistenceManager persistenceManager = new FilePersistenceManager(instancesDir);

        ProjectDefinitionAdapter adapter = new ProjectDefinitionAdapter(project, activeProfiles, baseDir);

        UserTaskHandler uth = new FormTaskHandler(contextFactory, adapter.forms(), formService);

        Configuration cfg = new Configuration();
        cfg.setInterpolateInputVariables(true);
        cfg.setWrapAllExceptionsAsBpmnErrors(true);
        cfg.setCopyAllCallActivityOutVariables(true);

        ElementEventProcessor eventProcessor;
        if (eventCfg.isRecordEvents()) {
            eventProcessor = new DefaultElementEventProcessor(apiClientFactory, adapter.processes());
        } else {
            eventProcessor = new NopElementEventProcessor();
        }
        ProcessOutVariables outVariables = new ProcessOutVariables(contextFactory);

        List<TaskInterceptor> taskInterceptors = new ArrayList<>();
        taskInterceptors.add(protectedVarContext);
        taskInterceptors.add(new TaskEventInterceptor(eventCfg, eventProcessor));
        taskInterceptors.add(new PolicyPreprocessor(baseDir));

        Engine engine = new EngineBuilder()
                .withContextFactory(contextFactory)
                .withLockManager(new NoopLockManager())
                .withExpressionManager(expressionManager)
                .withDefinitionProvider(adapter.processes())
                .withTaskRegistry(taskRegistry)
                .withJavaDelegateHandler(new JavaDelegateHandlerImpl(taskInterceptors))
                .withEventStorage(eventStorage)
                .withPersistenceManager(persistenceManager)
                .withUserTaskHandler(uth)
                .withConfiguration(cfg)
                .withListener(new ProcessOutVariablesListener(attachmentsDir, outVariables))
                .withListener(new VariablesSnapshotListener(stateDir))
                .withResourceResolver(new ResourceResolverImpl(baseDir))
                .build();

        engine.addInterceptor(new ProcessElementInterceptor(eventProcessor, new ProcessMetadataProcessor(apiClientFactory, metaVariables)));
        return engine;
    }

    private static class ProjectDefinitionAdapter {

        private final ProjectDefinition project;
        private final Collection<String> activeProfiles;

        private ProjectDefinitionAdapter(ProjectDefinition project, Collection<String> activeProfiles, Path baseDir) {
            this.project = project;
            this.activeProfiles = activeProfiles;
        }

        ProcessDefinitionProvider processes() {
            return id -> ProjectDefinitionUtils.getFlow(project, activeProfiles, id);
        }

        FormDefinitionProvider forms() {
            return id -> ProjectDefinitionUtils.getForm(project, activeProfiles, id);
        }
    }

    private static final class JavaDelegateHandlerImpl implements JavaDelegateHandler {

        private final List<TaskInterceptor> interceptors;

        private JavaDelegateHandlerImpl(List<TaskInterceptor> interceptors) {
            this.interceptors = interceptors;
        }

        @Override
        public void execute(Object task, ExecutionContext ctx) throws Exception {
            Named n = ReflectionUtils.findAnnotation(task.getClass(), Named.class);
            String taskName = null;
            if (n != null) {
                taskName = n.value();
            }

            // check JavaDelegate first because tasks can implement both JavaDelegate and Task
            if (task instanceof JavaDelegate) {
                JavaDelegate d = (JavaDelegate) task;
                preTask(taskName, task, (Context) ctx);
                d.execute(ctx);
                postTask(taskName, task, (Context) ctx);
            } else if (task instanceof Task) {
                Task t = (Task) task;
                preTask(taskName, task, (Context) ctx);
                t.execute((Context) ctx);
                postTask(taskName, task, (Context) ctx);
            } else {
                throw new ExecutionException("Unsupported task type: " + task + ": tasks must implement either " +
                        Task.class.getName() + " or " + JavaDelegate.class.getName() + " interfaces");
            }
        }

        private void preTask(String taskName, Object instance, Context ctx) throws ExecutionException {
            for (TaskInterceptor i : interceptors) {
                i.preTask(taskName, instance, ctx);
            }
        }

        private void postTask(String taskName, Object instance, Context ctx) throws ExecutionException {
            for (TaskInterceptor i : interceptors) {
                i.postTask(taskName, instance, ctx);
            }
        }
    }
}
