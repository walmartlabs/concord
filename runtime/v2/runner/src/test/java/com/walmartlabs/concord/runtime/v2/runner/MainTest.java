package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.WorkingDirectory;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.guice.BaseRunnerModule;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallPolicyChecker;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskResultListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskV2Provider;
import com.walmartlabs.concord.runtime.v2.sdk.DefaultVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.sdk.Constants;
import org.immutables.value.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MainTest {

    private Path workDir;
    private UUID instanceId;
    private FormService formService;
    private ProcessConfiguration processConfiguration;

    private ProcessStatusCallback processStatusCallback;
    private CheckpointService checkpointService;
    private Module testServices;

    private byte[] lastLog;

    @Before
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory("test");

        instanceId = UUID.randomUUID();

        Path formsDir = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME)
                .resolve(Constants.Files.JOB_FORMS_V2_DIR_NAME);

        formService = new FormService(formsDir);

        processStatusCallback = mock(ProcessStatusCallback.class);
        checkpointService = mock(CheckpointService.class);

        testServices = new AbstractModule() {
            @Override
            protected void configure() {
                install(new BaseRunnerModule());

                bind(CheckpointService.class).toInstance(checkpointService);
                bind(PersistenceService.class).toInstance(mock(PersistenceService.class));
                bind(ProcessStatusCallback.class).toInstance(processStatusCallback);

                Multibinder<TaskProvider> taskProviders = Multibinder.newSetBinder(binder(), TaskProvider.class);
                taskProviders.addBinding().to(TaskV2Provider.class);

                Multibinder<TaskCallListener> taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
                taskCallListeners.addBinding().to(TaskCallPolicyChecker.class);
                taskCallListeners.addBinding().to(TaskResultListener.class);
            }
        };
    }

    @After
    public void tearDown() throws IOException {
        if (workDir != null) {
            IOUtils.deleteRecursively(workDir);
        }
    }

    @Test
    public void test() throws Exception {
        deploy("hello");

        save(ProcessConfiguration.builder()
                .putArguments("name", "Concord")
                .putDefaultTaskVariables("testDefaults", Collections.singletonMap("a", "a-value"))
                .build());

        byte[] log = start();
        assertLog(log, ".*Hello, Concord!.*");
        assertLog(log, ".*" + Pattern.quote("defaultsMap:{a=a-value}") + ".*");
        assertLog(log, ".*" + Pattern.quote("defaultsTyped:Defaults{a=a-value}") + ".*");

        verify(processStatusCallback, times(1)).onRunning(instanceId);
    }

    @Test
    public void testForm() throws Exception {
        deploy("form");

        save(ProcessConfiguration.builder().build());

        byte[] log = start();
        assertLog(log, ".*Before.*");

        List<Form> forms = formService.list();
        assertEquals(1, forms.size());

        Form myForm = forms.get(0);
        assertEquals("myForm", myForm.name());

        // resume the process using the saved form

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", "John Smith");
        data.put("age", 33);

        log = resume(myForm.eventName(), ProcessConfiguration.builder().arguments(Collections.singletonMap("myForm", data)).build());
        assertLog(log, ".*After.*John Smith.*");
    }

    @Test
    public void testUnknownTask() throws Exception {
        deploy("unknownTask");

        save(ProcessConfiguration.builder().build());

        try {
            start();
            fail("must fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not found: unknown"));
        }
    }

    @Test
    public void testCheckpoints() throws Exception {
        deploy("checkpoints");

        save(ProcessConfiguration.builder()
                .putArguments("name", "Concord")
                .build());

        byte[] log = start();
        assertLog(log, ".*Hello, Concord!.*");

        verify(processStatusCallback, times(1)).onRunning(eq(instanceId));
        verify(checkpointService, times(1)).create(eq("A"), any(), any());
    }

    @Test
    public void testTaskResultPolicy() throws Exception {
        deploy("taskResultPolicy");

        save(ProcessConfiguration.builder().build());

        try {
            start();
            fail("exception expected");
        } catch (Exception e) {
            assertEquals("Found forbidden tasks", e.getMessage());
        }

        assertLog(lastLog, ".*forbidden by the task policy.*");
    }

    @Test
    public void testTaskInputInterpolate() throws Exception {
        deploy("taskInputInterpolate");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = start();
        assertLog(log, ".*Hello, " + Pattern.quote("${myFavoriteExpression}") + "!.*");
    }

    @Test
    public void testIfExpression() throws Exception {
        deploy("if-expression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "1")
                .build());

        byte[] log = start();
        assertLog(log, ".*it's clearly non-zero.*");

        verify(processStatusCallback, times(1)).onRunning(eq(instanceId));
    }

    @Test
    public void testSwitchExpressionCaseFound() throws Exception {
        deploy("switch-expression-full");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red")
                .build());

        byte[] log = start();
        assertLog(log, ".*It's red.*");
    }

    @Test
    public void testSwitchExpressionCaseNotFound() throws Exception {
        deploy("switch-expression-full");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red1")
                .build());

        byte[] log = start();
        assertLog(log, ".*I don't know what it is.*");
    }

    @Test
    public void testSwitchExpressionDefault() throws Exception {
        deploy("switch-expression-default");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red1")
                .build());

        byte[] log = start();
        assertLog(log, ".*I don't know what it is.*");
    }

    @Test
    public void testSwitchExpressionCaseExpression() throws Exception {
        deploy("switch-expression-case-expression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red")
                .putArguments("aKnownValue", "red")
                .build());

        byte[] log = start();
        assertLog(log, ".*Yes, I recognize this red.*");
    }

    @Test
    public void testSwitchExpressionCaseExpressionDefault() throws Exception {
        deploy("switch-expression-case-expression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "boo")
                .putArguments("aKnownValue", "red")
                .build());

        byte[] log = start();
        assertLog(log, ".*Nope.*");
    }

    @Test
    public void testScriptInline() throws Exception {
        deploy("script-inline");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = start();
        assertLog(log, ".*x: 1.*");
    }

    @Test
    public void testScriptAttached() throws Exception {
        deploy("script-attached");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = start();
        assertLog(log, ".*x: 1.*");
    }

    @Test
    public void testScriptErrorBlock() throws Exception {
        deploy("script-error");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = start();
        assertLog(log, ".*error occurred: java.lang.RuntimeException: Error: this is an error in <eval> at line number 1 at column number 0.*");
    }

    @Test
    public void testInitiator() throws Exception {
        deploy("initiator");

        Map<String, Object> initiator = new HashMap<>();
        initiator.put("username", "test");
        initiator.put("displayName", "Test User");

        save(ProcessConfiguration.builder()
                .initiator(initiator)
                .putArguments("name", "${initiator.displayName}")
                .build());

        byte[] log = start();
        assertLog(log, ".*Test User.*");
    }

    private void deploy(String resource) throws URISyntaxException, IOException {
        Path src = Paths.get(MainTest.class.getResource(resource).toURI());
        IOUtils.copy(src, workDir);
    }

    private void save(ProcessConfiguration cfg) {
        this.processConfiguration = ProcessConfiguration.builder().from(cfg)
                .instanceId(instanceId)
                .build();
    }

    private byte[] start() throws Exception {
        return run();
    }

    private byte[] resume(String eventName, ProcessConfiguration cfg) throws Exception {
        StateManager.saveResumeEvent(workDir, eventName); // TODO use interface
        if (cfg != null) {
            save(cfg);
        }
        return run();
    }

    private byte[] run() throws Exception {
        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .agentId(UUID.randomUUID().toString())
                .api(ApiConfiguration.builder()
                        .baseUrl("http://localhost:8001") // TODO make optional?
                        .build())
                .build();

        PrintStream oldOut = System.out;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        System.setOut(out);

        byte[] log;
        try {
            Injector injector = new InjectorFactory(new WorkingDirectory(workDir),
                    runnerCfg,
                    () -> processConfiguration,
                    testServices)
                    .create();

            injector.getInstance(Main.class).execute();
        } finally {
            out.flush();
            System.setOut(oldOut);

            log = baos.toByteArray();
            System.out.write(log, 0, log.length);

            lastLog = log;
        }

        return log;
    }

    private static void assertLog(byte[] ab, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(ab);
             BufferedReader reader = new BufferedReader(new InputStreamReader(bais))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(pattern)) {
                    return;
                }
            }

            fail("Expected a log entry: " + pattern + ", got: \n" + new String(ab));
        }
    }

    @Named("testDefaults")
    static class TestDefaults implements Task {

        @DefaultVariables("testDefaults")
        Map<String, Object> defaultsMap;

        @DefaultVariables
        Defaults defaultsTyped;

        @Override
        public Serializable execute(TaskContext ctx) {
            System.out.println("defaultsMap:" + defaultsMap);
            System.out.println("defaultsTyped:" + defaultsTyped);
            return null;
        }

        @Value.Immutable
        @JsonSerialize(as = ImmutableDefaults.class)
        @JsonDeserialize(as = ImmutableDefaults.class)
        public interface Defaults {

            String a();

            @Nullable
            String b();
        }
    }

    @Named("wrapExpression")
    static class WrapExpressionTask implements Task {

        @Override
        public Serializable execute(TaskContext ctx) {
            return "${" + ctx.input().get("expression") + "}";
        }
    }

    @Named("testTask")
    static class TestTask implements Task {

        @Override
        public Serializable execute(TaskContext ctx) throws Exception {
            return new HashMap<>(ctx.input());
        }
    }
}
