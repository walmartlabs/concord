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
import com.walmartlabs.concord.runtime.common.cfg.ImmutableRunnerConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.guice.BaseRunnerModule;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingConfigurator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallPolicyChecker;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskResultListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskV2Provider;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.v1.compat.V1CompatModule;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.svm.ExecutionListener;
import org.immutables.value.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private Path segmentedLogDir;

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

                Multibinder.newSetBinder(binder(), ExecutionListener.class);
            }
        };

        segmentedLogDir = Files.createTempDirectory("segmentedLog");
    }

    @After
    public void tearDown() throws IOException {
        if (workDir != null) {
            IOUtils.deleteRecursively(workDir);
        }

        if (segmentedLogDir != null) {
            IOUtils.deleteRecursively(segmentedLogDir);
        }

        LoggingConfigurator.reset();
    }

    @Test
    public void test() throws Exception {
        deploy("hello");

        save(ProcessConfiguration.builder()
                .putArguments("name", "Concord")
                .putDefaultTaskVariables("testDefaults", Collections.singletonMap("a", "a-value"))
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, Concord!.*");
        assertLog(log, ".*" + Pattern.quote("defaultsMap:{a=a-value}") + ".*");
        assertLog(log, ".*" + Pattern.quote("defaultsTyped:Defaults{a=a-value}") + ".*");

        verify(processStatusCallback, times(1)).onRunning(instanceId);
    }

    @Test
    public void testForm() throws Exception {
        deploy("form");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
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
            run();
            fail("must fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not found: unknown"));
        }
    }

    @Test
    public void testTaskErrorBlock() throws Exception {
        deploy("faultyTask");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*error occurred:.*boom!.*");
    }

    @Test
    public void testTryErrorBlock() throws Exception {
        deploy("tryError");

        save(ProcessConfiguration.builder().build());

        try {
            run();
            fail("should fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("boom!"));
        }

        assertLog(lastLog, ".*error occurred:.*boom!.*");
    }

    @Test
    public void testCheckpoints() throws Exception {
        deploy("checkpoints");

        save(ProcessConfiguration.builder()
                .putArguments("name", "Concord")
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, Concord!.*");

        verify(processStatusCallback, times(1)).onRunning(eq(instanceId));
        verify(checkpointService, times(1)).create(eq("A"), any(), any());
    }

    @Test
    public void testTaskResultPolicy() throws Exception {
        deploy("taskResultPolicy");

        save(ProcessConfiguration.builder().build());

        try {
            run();
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

        byte[] log = run();
        assertLog(log, ".*Hello, " + Pattern.quote("${myFavoriteExpression}") + "!.*");
    }

    @Test
    public void testIfExpression() throws Exception {
        deploy("ifExpression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "1")
                .build());

        byte[] log = run();
        assertLog(log, ".*it's clearly non-zero.*");

        verify(processStatusCallback, times(1)).onRunning(eq(instanceId));
    }

    @Test
    public void testSwitchExpressionCaseFound() throws Exception {
        deploy("switchExpressionFull");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red")
                .build());

        byte[] log = run();
        assertLog(log, ".*It's red.*");
    }

    @Test
    public void testSwitchExpressionCaseNotFound() throws Exception {
        deploy("switchExpressionFull");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red1")
                .build());

        byte[] log = run();
        assertLog(log, ".*I don't know what it is.*");
    }

    @Test
    public void testSwitchExpressionDefault() throws Exception {
        deploy("switchExpressionDefault");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red1")
                .build());

        byte[] log = run();
        assertLog(log, ".*I don't know what it is.*");
    }

    @Test
    public void testSwitchExpressionCaseExpression() throws Exception {
        deploy("switchExpressionCaseExpression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red")
                .putArguments("aKnownValue", "red")
                .build());

        byte[] log = run();
        assertLog(log, ".*Yes, I recognize this red.*");
    }

    @Test
    public void testSwitchExpressionCaseExpressionDefault() throws Exception {
        deploy("switchExpressionCaseExpression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "boo")
                .putArguments("aKnownValue", "red")
                .build());

        byte[] log = run();
        assertLog(log, ".*Nope.*");
    }

    @Test
    public void testScriptInline() throws Exception {
        deploy("scriptInline");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x: 1.*");
    }

    @Test
    public void testScriptAttached() throws Exception {
        deploy("scriptAttached");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x: 1.*");
    }

    @Test
    public void testScriptErrorBlock() throws Exception {
        deploy("scriptError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*error occurred: java.lang.RuntimeException: Error: this is an error in <eval> at line number 1 at column number 0.*");
    }

    @Test
    public void testNonSerializableLocal() throws Exception {
        deploy("nonSerializableLocal");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*error occurred:.*Can't set a non-serializable local variable.*");
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

        byte[] log = run();
        assertLog(log, ".*Test User.*");
    }

    @Test
    public void testSegmentedLogging() throws Exception {
        deploy("logging");

        save(ProcessConfiguration.builder()
                .build());

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogDir(segmentedLogDir.toAbsolutePath().toString())
                        .build())
                .build();

        byte[] log = run(runnerCfg);
        assertLog(log, ".*This goes directly into the stdout.*");
        assertNoLog(log, ".*This is a processLog entry.*");

        List<Path> paths = Files.list(segmentedLogDir).collect(Collectors.toList());
        assertNotEquals(0, paths.size());
    }

    @Test
    public void testMultipleWithItems() throws Exception {
        deploy("multipleWithItems");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        for (int i = 1; i < 7; i++) {
            assertLog(log, ".*item: " + i + ".*");
        }
    }

    @Test
    public void testUnknownMethod() throws Exception {
        deploy("unknownMethod");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("should fail");
        } catch (Exception e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Can't find 'sayGoodbye' method"));
            assertTrue(msg.contains("Did you mean: 'sayHello'"));
        }
    }

    @Test
    public void testSuspend() throws Exception {
        deploy("suspend");

        save(ProcessConfiguration.builder()
                .putArguments("testValue", "XYZ")
                .build());

        byte[] log = run();
        assertLog(log, ".*aaa.*");

        log = resume("ev1", ProcessConfiguration.builder().build());
        assertLog(log, ".*XYZ.*");
    }

    @Test
    public void testDefaultProcessVariables() throws Exception {
        deploy("defaultVariables");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*workDir: '.*'.*");
        assertLog(log, ".*processInfo: '\\{.*\\}'.*");
        assertLog(log, ".*projectInfo: '\\{\\}'.*");

        log = resume("ev1", ProcessConfiguration.builder()
                .putArguments("workDir", "1")
                .putArguments("processInfo", "2")
                .putArguments("projectInfo", "3")
                .build());
        assertLog(log, ".*workDir: '.*'.*");
        assertLog(log, ".*processInfo: '\\{.*\\}'.*");
        assertLog(log, ".*projectInfo: '\\{\\}'.*");
    }

    @Test
    public void testSetVariables() throws Exception {
        deploy("setVariables");

        save(ProcessConfiguration.builder()
                .putArguments("k1", "XYZ")
                .putArguments("k2", "init")
                .build());

        byte[] log = run();
        assertLog(log, ".*k1-value, init, k3-value.*");
    }

    @Test
    public void testReturn() throws Exception {
        deploy("return");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*before return.*");
        assertNoLog(log, ".*after return.*");
    }

    @Test
    public void testExit() throws Exception {
        deploy("exit");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*before exit.*");
        assertNoLog(log, ".*after exit.*");
    }

    @Test
    public void testCallFlow() throws Exception {
        deploy("call");

        save(ProcessConfiguration.builder()
                .putArguments("flowName", "myFlow")
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("Hello, default flow!") + ".*");
        assertLog(log, ".*" + Pattern.quote("Hello, myFlow flow!") + ".*");
    }

    @Test
    public void testV1Compat() throws Exception {
        deploy("v1Compat");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*execute: p1=ABC.*");
        assertLog(log, ".*" + Pattern.quote("result: {result=abc}") + ".*");
    }

    @Test
    public void testVarScoping() throws Exception {
        deploy("varScoping");

        save(ProcessConfiguration.builder()
                .putArguments("x", 123)
                .build());

        byte[] log = run();
        assertLog(log, ".*1: 123.*");
        assertLog(log, ".*a: 123.*");
        assertLog(log, ".*2: false.*");
        assertLog(log, ".*3: 345.*");
        assertLog(log, ".*4: 456.*");
        assertLog(log, ".*c: 456.*");
        assertLog(log, ".*5: 567.*");

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

    private byte[] resume(String eventName, ProcessConfiguration cfg) throws Exception {
        StateManager.saveResumeEvent(workDir, eventName); // TODO use interface
        if (cfg != null) {
            save(cfg);
        }
        return run();
    }

    private byte[] run() throws Exception {
        return run(null);
    }

    private byte[] run(RunnerConfiguration baseCfg) throws Exception {
        ImmutableRunnerConfiguration.Builder runnerCfg = RunnerConfiguration.builder();

        if (baseCfg != null) {
            runnerCfg.from(baseCfg);
        }

        runnerCfg.agentId(UUID.randomUUID().toString())
                .api(ApiConfiguration.builder()
                        .baseUrl("http://localhost:8001") // TODO make optional?
                        .build());

        PrintStream oldOut = System.out;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        System.setOut(out);

        AbstractModule runtimeModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(DefaultTaskVariablesService.class).toProvider(new DefaultTaskVariablesProvider(processConfiguration));
            }
        };

        byte[] log;
        try {
            Injector injector = new InjectorFactory(new WorkingDirectory(workDir),
                    runnerCfg.build(),
                    () -> processConfiguration,
                    testServices,
                    runtimeModule,
                    new V1CompatModule()) // allow runtime v1 tasks
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

        if (grep(ab, pattern) != 1) {
            fail("Expected a single log entry: " + pattern + ", got: \n" + new String(ab));
        }
    }

    private static void assertNoLog(byte[] ab, String pattern) throws IOException {
        if (grep(ab, pattern) > 0) {
            fail("Expected no log entries like this: " + pattern + ", got: \n" + new String(ab));
        }
    }

    private static int grep(byte[] ab, String pattern) throws IOException {
        int cnt = 0;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(ab);
             BufferedReader reader = new BufferedReader(new InputStreamReader(bais))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(pattern)) {
                    cnt++;
                }
            }
        }

        return cnt;
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
    @SuppressWarnings("unused")
    static class WrapExpressionTask implements Task {

        @Override
        public Serializable execute(TaskContext ctx) {
            return "${" + ctx.input().get("expression") + "}";
        }
    }

    @Named("testTask")
    @SuppressWarnings("unused")
    static class TestTask implements Task {

        @Override
        public Serializable execute(TaskContext ctx) {
            return new HashMap<>(ctx.input());
        }
    }

    @Named("loggingExample")
    @SuppressWarnings("unused")
    static class LoggingExampleTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(LoggingExampleTask.class);
        private static final Logger processLog = LoggerFactory.getLogger("processLog");

        @Override
        public Serializable execute(TaskContext ctx) throws Exception {
            log.info("This goes into a regular log");
            processLog.info("This is a processLog entry");
            System.out.println("This goes directly into the stdout");

            ExecutorService executor = Executors.newCachedThreadPool();

            for (int i = 0; i < 5; i++) {
                final int n = i;
                executor.submit(() -> {
                    Logger log = LoggerFactory.getLogger("taskThread" + n);
                    log.info("Hey, I'm a task thread #" + n);
                });
            }

            executor.shutdown();
            executor.awaitTermination(100, TimeUnit.SECONDS);

            return null;
        }
    }

    @Named("unknownMethod")
    @SuppressWarnings("unused")
    static class UnknownMethodTask implements Task {

        public String sayHello() {
            return "Hello!";
        }
    }

    @Named("v1")
    @SuppressWarnings("unused")
    static class V1Task implements com.walmartlabs.concord.sdk.Task {

        @InjectVariable("context")
        com.walmartlabs.concord.sdk.Context ctx;

        @Override
        public void execute(com.walmartlabs.concord.sdk.Context ctx) {
            assertEquals(ctx, this.ctx);

            ctx.setVariable("result", "abc");
            System.out.println("execute: p1=" + ctx.getVariable("p1"));
        }

        // can't use in v2
        public String callWithoutContext() {
            return "without-context";
        }

        // can't use in v2
        public String callWithContext(@InjectVariable("context") com.walmartlabs.concord.sdk.Context ctx) {
            ctx.setVariable("result2", "xyz");
            return "with-context";
        }
    }

    @Named("faultyTask")
    @SuppressWarnings("unused")
    static class FaultyTask implements Task {

        @Override
        public Serializable execute(TaskContext ctx) {
            throw new RuntimeException("boom!");
        }
    }
}
