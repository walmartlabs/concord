package com.walmartlabs.concord.project.yaml;

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

import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import io.takari.bpm.Configuration;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.Engine;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.context.DefaultExecutionContextFactory;
import io.takari.bpm.el.DefaultExpressionManager;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.form.*;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import io.takari.bpm.resource.ResourceResolver;
import io.takari.bpm.task.ServiceTaskRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;

public abstract class AbstractYamlParserTest {

    private TestWorkflowProvider workflowProvider;
    private TestServiceTaskRegistry taskRegistry;
    private Engine engine;
    private Map<UUID, Form> forms;
    private FormService formService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        workflowProvider = new TestWorkflowProvider();

        taskRegistry = new TestServiceTaskRegistry();

        forms = new HashMap<>();
        FormStorage fs = new TestFormStorage(forms);

        ExpressionManager expressionManager = new DefaultExpressionManager(taskRegistry);
        DefaultExecutionContextFactory contextFactory = new DefaultExecutionContextFactory(expressionManager);

        DefaultFormService.ResumeHandler rs = (form, args) -> getEngine().resume(form.getProcessBusinessKey(), form.getEventName(), args);
        formService = new DefaultFormService(contextFactory, rs, fs) {
            @Override
            public FormField toFormField(Map<String, Object> m) {
                Map.Entry<String, Object> entry = m.entrySet().iterator().next();
                Map<String, Object> opts = (Map<String, Object>) entry.getValue();
                return new FormField.Builder(entry.getKey(), (String) opts.get("type"))
                        .build();
            }
        };

        ResourceResolver resourceResolver = ClassLoader::getSystemResourceAsStream;

        Configuration cfg = new Configuration();
        cfg.setInterpolateInputVariables(true);
        cfg.setWrapAllExceptionsAsBpmnErrors(true);
        cfg.setCopyAllCallActivityOutVariables(true);

        engine = new EngineBuilder()
                .withDefinitionProvider(workflowProvider.processes())
                .withTaskRegistry(taskRegistry)
                .withUserTaskHandler(new FormTaskHandler(contextFactory, workflowProvider.forms(), formService))
                .withResourceResolver(resourceResolver)
                .withConfiguration(cfg)
                .build();
    }

    protected ProcessDefinition getDefinition(String id) throws ExecutionException {
        return workflowProvider.processes().getById(id);
    }

    protected void deploy(String resource) {
        workflowProvider.deploy(resource);
    }

    protected void register(String name, Object t) {
        taskRegistry.register(name, t);
    }

    protected void start(String processBusinessKey, String processDefinitionId) throws ExecutionException {
        start(processBusinessKey, processDefinitionId, null);
    }

    protected void start(String processBusinessKey, String processDefinitionId, Map<String, Object> variables) throws ExecutionException {
        engine.start(processBusinessKey, processDefinitionId, variables);
    }

    protected void resume(String processBusinessKey, String eventName) throws ExecutionException {
        engine.resume(processBusinessKey, eventName, null);
    }

    protected Form getForm(UUID formId) throws ExecutionException {
        return formService.get(formId);
    }

    protected UUID getFirstFormId() {
        if (forms == null || forms.isEmpty()) {
            return null;
        }
        return forms.keySet().iterator().next();
    }

    protected FormSubmitResult submitForm(UUID formInstanceId, Map<String, Object> data) throws ExecutionException {
        return formService.submit(formInstanceId, data);
    }

    private Engine getEngine() {
        return engine;
    }

    private static class TestFormStorage implements FormStorage {

        private final Map<UUID, Form> forms;

        private TestFormStorage(Map<UUID, Form> forms) {
            this.forms = forms;
        }

        @Override
        public void save(Form form) {
            forms.put(form.getFormInstanceId(), form);
        }

        @Override
        public void complete(UUID formInstanceId) {
            forms.remove(formInstanceId);
        }

        @Override
        public Form get(UUID formId) {
            return forms.get(formId);
        }
    }

    private static class TestServiceTaskRegistry implements ServiceTaskRegistry {

        private final Map<String, Object> entries = new HashMap<>();

        public void register(String k, Object v) {
            entries.put(k, v);
        }

        @Override
        public Object getByKey(String k) {
            return entries.get(k);
        }
    }

    private static class TestWorkflowProvider {

        private final ProjectLoader projectLoader = new ProjectLoader(mock(ImportManager.class));
        private final Map<String, ProcessDefinition> processes = new HashMap<>();
        private final Map<String, FormDefinition> forms = new HashMap<>();

        public void deploy(String resource) {
            ProjectDefinition wd = loadWorkflow(resource);
            processes.putAll(wd.getFlows());
            forms.putAll(wd.getForms());
        }

        public ProcessDefinitionProvider processes() {
            return processes::get;
        }

        public FormDefinitionProvider forms() {
            return forms::get;
        }

        private ProjectDefinition loadWorkflow(String resource) {
            try (InputStream in = ClassLoader.getSystemResourceAsStream(resource)) {
                return projectLoader.loadProject(in).getProjectDefinition();
            } catch (Exception e) {
                throw new RuntimeException("Error while loading a definition", e);
            }
        }
    }
}
