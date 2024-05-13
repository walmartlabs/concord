package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.google.inject.name.Names;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.common.SerializationUtils;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.ImmutableRunnerConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointUploader;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.DefaultCheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.guice.BaseRunnerModule;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggerProvider;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingClient;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingConfigurator;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallPolicyChecker;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskResultListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskV2Provider;
import com.walmartlabs.concord.runtime.v2.runner.vm.BlockCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.ParallelCommand;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.immutables.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.quote;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MainTest {

    private Path workDir;
    private UUID instanceId;
    private FormService formService;
    private ProcessConfiguration processConfiguration;

    private ProcessStatusCallback processStatusCallback;
    private Module testServices;

    private TestCheckpointUploader checkpointService;

    private byte[] lastLog;
    private byte[] allLogs;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        workDir = Files.createTempDirectory("test");

        instanceId = UUID.randomUUID();

        Path formsDir = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME)
                .resolve(Constants.Files.JOB_FORMS_V2_DIR_NAME);

        formService = new FormService(formsDir);

        processStatusCallback = mock(ProcessStatusCallback.class);

        checkpointService = spy(new TestCheckpointUploader());

        testServices = new AbstractModule() {
            @Override
            protected void configure() {
                install(new BaseRunnerModule());

                bind(ClassLoader.class).annotatedWith(Names.named("runtime")).toInstance(MainTest.class.getClassLoader());

                bind(CheckpointUploader.class).toInstance(checkpointService);
                bind(CheckpointService.class).to(DefaultCheckpointService.class);
                bind(DependencyManager.class).to(DefaultDependencyManager.class);
                bind(DockerService.class).to(DefaultDockerService.class);
                bind(FileService.class).to(DefaultFileService.class);
                bind(LockService.class).to(DefaultLockService.class);
                bind(PersistenceService.class).toInstance(mock(PersistenceService.class));
                bind(ProcessStatusCallback.class).toInstance(processStatusCallback);
                bind(SecretService.class).to(DefaultSecretService.class);
                bind(ApiClient.class).toInstance(mock(ApiClient.class));

                Multibinder<TaskProvider> taskProviders = Multibinder.newSetBinder(binder(), TaskProvider.class);
                taskProviders.addBinding().to(TaskV2Provider.class);

                Multibinder<TaskCallListener> taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
                taskCallListeners.addBinding().to(TaskCallPolicyChecker.class);
                taskCallListeners.addBinding().to(TaskResultListener.class);

                Multibinder<ExecutionListener> executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
                executionListeners.addBinding().toInstance(new ExecutionListener(){
                    @Override
                    public void beforeProcessStart() {
                        SensitiveDataHolder.getInstance().get().clear();
                    }
                });
                executionListeners.addBinding().to(StackTraceCollector.class);

                boolean ignoreSerializationAssert = testInfo.getTestMethod()
                        .filter(m -> m.getAnnotation(IgnoreSerializationAssert.class) != null)
                        .isPresent();
                if (!ignoreSerializationAssert) {
                    executionListeners.addBinding().toInstance(new ExecutionListener() {
                        @Override
                        public Result afterCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
                            if (cmd instanceof BlockCommand
                                    || cmd instanceof ParallelCommand) {
                                return ExecutionListener.super.afterCommand(runtime, vm, state, threadId, cmd);
                            }

                            assertTrue(SerializationUtils.isSerializable(state), "Non serializable state after: " + cmd);
                            return ExecutionListener.super.afterCommand(runtime, vm, state, threadId, cmd);
                        }
                    });
                }
            }
        };

        allLogs = null;
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (workDir != null) {
            IOUtils.deleteRecursively(workDir);
        }

        LoggingConfigurator.reset();
    }

    @Test
    public void testVariablesAfterResume() throws Exception {
        deploy("variablesAfterResume");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*workDir1: " + workDir.toAbsolutePath() + ".*");
        assertLog(log, ".*workDir3: " + workDir.toAbsolutePath() + ".*");

        List<Form> forms = formService.list();
        assertEquals(1, forms.size());

        Form myForm = forms.get(0);
        assertEquals("myForm", myForm.name());

        // resume the process using the saved form

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", "John Smith");

        Path newWorkDir = Files.createTempDirectory("test-new");
        IOUtils.copy(workDir, newWorkDir);
        workDir = newWorkDir;

        log = resume(myForm.eventName(), ProcessConfiguration.builder().arguments(Collections.singletonMap("myForm", data)).build());
        assertLog(log, ".*workDir4: " + workDir.toAbsolutePath() + ".*");
        assertLog(log, ".*workDir2: " + workDir.toAbsolutePath() + ".*");
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
        assertLog(log, ".*k: \"value\".*");

        verify(processStatusCallback, times(1)).onRunning(instanceId);
    }

    @Test
    public void testFlowNameVariable() throws Exception {
        deploy("doNotTouchFlowNameVariable");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*flowName in inner flow: 'This is MY variable'.*");

        verify(processStatusCallback, times(1)).onRunning(instanceId);
    }

    @Test
    public void testStackTrace() throws Exception {
        deploy("stackTrace");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(lastLog, ".*" + Pattern.quote("(concord.yml) @ line: 9, col: 7, thread: 0, flow: flowB") + ".*");
        assertLog(lastLog, ".*" + Pattern.quote("(concord.yml) @ line: 3, col: 7, thread: 0, flow: flowA") + ".*");
    }

    @Test
    public void testStackTrace2() throws Exception {
        deploy("stackTrace2");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(lastLog, ".*" + Pattern.quote("(concord.yml) @ line: 10, col: 7, thread: 1, flow: flowB") + ".*");
        assertLog(lastLog, ".*" + Pattern.quote("(concord.yml) @ line: 4, col: 11, thread: 1, flow: flowA") + ".*");

        assertLog(lastLog, ".*" + Pattern.quote("(concord.yml) @ line: 5, col: 11, thread: 2, flow: flowB") + ".*");
    }

    @Test
    public void testStackTrace3() throws Exception {
        deploy("stackTrace3");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(lastLog, ".*" + Pattern.quote("in flowA") + ".*");

        String expected = "Call stack:\n" +
                "(concord.yml) @ line: 13, col: 7, thread: 2, flow: flowB\n" +
                "(concord.yml) @ line: 3, col: 7, thread: 2, flow: flowA";

        String logString = new String(lastLog);
        assertTrue(logString.contains(expected), "expected log contains: " + expected + ", actual: " + logString);
    }

    @Test
    public void testStackTrace4() throws Exception {
        deploy("stackTrace4");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String expected = "Call stack:\n" +
                "(concord.yml) @ line: 8, col: 7, thread: 0, flow: flowB\n" +
                "(concord.yml) @ line: 3, col: 7, thread: 0, flow: flowA";

        String expected1 = "Call stack:\n" +
                "(concord.yml) @ line: 16, col: 11, thread: 0, flow: flowThrow\n" +
                "(concord.yml) @ line: 8, col: 7, thread: 0, flow: flowB\n" +
                "(concord.yml) @ line: 3, col: 7, thread: 0, flow: flowA";

        String logString = new String(lastLog);
        assertTrue(logString.contains(expected), "expected log contains: " + expected + ", actual: " + logString);
        assertTrue(logString.contains(expected1), "expected log contains: " + expected1 + ", actual: " + logString);
    }

    @Test
    public void testStackTrace5() throws Exception {
        deploy("stackTrace5");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String expected = ".*Call stack:\n" +
                "\\(concord.yml\\) @ line: 21, col: 7, thread: .*, flow: flowC\n" +
                "\\(concord.yml\\) @ line: 11, col: 11, thread: 2, flow: flowB\n" +
                "\\(concord.yml\\) @ line: 6, col: 7, thread: 0, flow: flowA\n" +
                "\\(concord.yml\\) @ line: 3, col: 7, thread: 0, flow: flow0.*";
        Pattern expectedPattern = Pattern.compile(expected, Pattern.MULTILINE|Pattern.DOTALL|Pattern.UNIX_LINES);

        String logString = new String(lastLog);
        assertTrue(expectedPattern.matcher(logString).matches(), "expected log contains: " + expected + ", actual: " + logString);
    }

    @Test
    public void testStackTrace6() throws Exception {
        deploy("stackTrace6");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        assertNoLog(lastLog, ".*" + Pattern.quote("[ERROR] Call stack:") + ".*");
    }

    @Test
    public void testStackTrace7() throws Exception {
        deploy("stackTrace7");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertNoLog(lastLog, ".*" + Pattern.quote("[ERROR] Call stack:") + ".*");
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
            assertTrue(e.getMessage().contains("not found: 'unknown'"));
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
    public void testTaskErrorOut() throws Exception {
        deploy("faultyTaskOut");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*result.key: value.*");
    }

    @Test
    public void testTaskIgnoreErrors() throws Exception {
        deploy("taskIgnoreErrors");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*ok: false.*");
        assertLog(log, ".*error:.*boom!.*");
    }

    @Test
    public void testTaskIgnoreErrors2() throws Exception {
        deploy("taskIgnoreErrors2");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*ok: false.*");
        assertLog(log, ".*error:.*boom!.*");
    }

    @Test
    public void testFlowErrorBlock() throws Exception {
        deploy("faultyFlow");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*error occurred:.*BOO.*");
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
        verify(checkpointService, times(1)).upload(any(), any(), eq("A"), any());
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
        assertLog(log, ".*error occurred: java.lang.RuntimeException: Error: this is an error.*");
    }

    @Test
    public void testScriptVersion() throws Exception {
        deploy("scriptEsVersion");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*\"charCountProduct\":9.*");
    }

    @Test
    public void testScriptEsVersionInvalid() throws Exception {
        deploy("scriptEsVersionInvalid");

        save(ProcessConfiguration.builder()
                .putArguments("kv", Collections.singletonMap("k", "v"))
                .build());

        try {
            run();
        } catch (Exception e) {
            assertLog(e.toString().getBytes(), ".*unsupported.*");
            return;
        }
        throw new Exception("invalid esVersion should have thrown");
    }


    @Test
    public void testScriptUnboundedInputMapOk() throws Exception {
        deploy("scriptUnboundedInputMapOk");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*ok.*");
    }

    @Test
    public void testScriptVariablesSanitize() throws Exception {
        deploy("scriptVariablesSanitize");

        save(ProcessConfiguration.builder()
                .putArguments("kv", Collections.singletonMap("k", "v"))
                .build());

        byte[] log = run();
        assertLog(log, ".*boom: \\{\\}.*");
    }

    @Test
    public void testScriptOut() throws Exception {
        deploy("scriptOut");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result.boom: \\{\\}.*");
        assertLog(log, ".*result.k1: value1");
        assertLog(log, ".*result.k2: value2");
    }

    @Test
    public void testScriptOutExpr() throws Exception {
        deploy("scriptOutExpr");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*k1: value1");
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
                        .sendSystemOutAndErrToSLF4J(false)
                        .build())
                .build();

        byte[] log = run(runnerCfg);
        assertLog(log, "^This goes directly into the stdout$");
        assertLog(log, ".*This is a processLog entry.*");
    }

    @Test
    public void throwStepShouldContainErrorDescription() throws Exception {
        deploy("logSegments1");

        save(ProcessConfiguration.builder()
                .build());

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        try {
            run(runnerCfg);
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // 83 log message length, 1 - segment id
        assertLog(lastLog, ".*" + Pattern.quote("|83|1|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. BOOM"));
    }

    @Test
    public void loopStepShouldLogErrorInProperLogSegment() throws Exception {
        deploy("logSegments2");

        save(ProcessConfiguration.builder()
                .build());

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        try {
            run(runnerCfg);
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // 129 log message length, 1, 2, 3, 4, 5, 6 - segment ids
        assertLog(lastLog, ".*" + Pattern.quote("|129|1|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|2|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|3|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|4|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|5|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|6|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
    }

    @Test
    public void testSystemOutRedirectInScripts() throws Exception {
        deploy("systemOutRedirect");

        save(ProcessConfiguration.builder()
                .build());

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .build())
                .build();

        byte[] log = run(runnerCfg);
        assertLog(log, "^.*\\|1\\|.*System.out in a script.*");
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
    @IgnoreSerializationAssert
    public void testParallelWithItemsTask() throws Exception {
        deploy("parallelWithItemsTask");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: \\[10, 20, 30\\].*");
        assertLog(log, ".*threadIds: \\[1, 2, 3].*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelLoopTask() throws Exception {
        deploy("parallelLoopTask");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: \\[10, 20, 30\\].*");
        assertLog(log, ".*threadIds: \\[1, 2, 3].*");
    }

    @Test
    public void testWithItemsBlock() throws Exception {
        deploy("withItemsBlock");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: \\[10, 20, 30\\].*");
    }

    @Test
    public void testLoopBlock() throws Exception {
        deploy("loopBlock");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: \\[10, 20, 30\\].*");
    }

    @Test
    public void testWithItemsSet() throws Exception {
        deploy("withItemsSet");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLogAtLeast(log, 3, ".*empty: \\[\\].*");
        assertLog(log, ".*after add: \\[1\\].*");
        assertLog(log, ".*after add: \\[2\\].*");
        assertLog(log, ".*after add: \\[3\\].*");
    }

    @Test
    public void testLoopSet() throws Exception {
        deploy("loopSet");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLogAtLeast(log, 3, ".*empty: \\[\\].*");
        assertLog(log, ".*after add: \\[1\\].*");
        assertLog(log, ".*after add: \\[2\\].*");
        assertLog(log, ".*after add: \\[3\\].*");
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
            assertTrue(msg.contains("Can't find 'sayGoodbye()' method"), msg);
            assertTrue(msg.contains("Did you mean: sayHello()"));
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
    public void testCallFlowWithErrorBlock() throws Exception {
        deploy("callWithErrorBlock");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*variable has been planted.*");
        assertLog(log, ".*myVar should exist: world.*");
    }

    @Test
    public void testVariableVisibilityAfterErrorBlocks() throws Exception {
        deploy("errorBlockScoping");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, world!.*");
    }

    @Test
    public void testCallFlowOut() throws Exception {
        deploy("callOut");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("single out a=a-value") + ".*");
        assertLog(log, ".*" + quote("array out a=a-value, b=b-value") + ".*");
        assertLog(log, ".*" + quote("expression out a=a-value, xx=123, zz=10000") + ".*");
        assertLog(log, ".*" + quote("out after suspend: a=aaa-value") + ".*");

        verify(checkpointService, times(1)).upload(any(), any(), eq("A"), any());
    }

    @Test
    public void testCallOutWithItems() throws Exception {
        deploy("callOutWithItems");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("single out x=[10, 20, 30]") + ".*");
        assertLog(log, ".*" + quote("array out: x=[10, 20, 30]") + ".*");
        assertLog(log, ".*" + quote("expression out: x=[10, 20, 30]") + ".*");
        assertLog(log, ".*" + quote("expression out: xx=[100, 200, 300]") + ".*");
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

    @Test
    @IgnoreSerializationAssert
    public void testParallelIn() throws Exception {
        deploy("parallelIn");

        save(ProcessConfiguration.builder()
                .putArguments("x", 123)
                .putArguments("y", 234)
                .build());

        byte[] log = run();
        assertLog(log, ".*thread A, x: 123.*");
        assertLog(log, ".*thread B, y: 234.*");
        assertLog(log, ".*thread C, x: 999.*");
        assertLog(log, ".*main, x: 123.*");
        assertLog(log, ".*main, y: 234.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelOut() throws Exception {
        deploy("parallelOut");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x: 123.*");
        assertLog(log, ".*y: 234.*");
    }

    @Test
    public void testSerialLoopEmptyCall() throws Exception {
        deploy("serialEmptyCall");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("outVar: [null, null]") + ".*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelLoopEmptyCall() throws Exception {
        deploy("parallelEmptyCall");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("outVar: [null, null]") + ".*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelOutExpr() throws Exception {
        deploy("parallelOutExpr");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x: 123.*");
        assertLog(log, ".*y: \\{inner=234\\}.*");
    }

    @Test
    public void testReentrant() throws Exception {
        deploy("reentrantTask");
        save(ProcessConfiguration.builder()
                .putArguments("actionName", "boo")
                .build());

        byte[] log = run();
        assertLog(log, ".*execute .*action=boo.*");

        log = resume(ReentrantTaskExample.EVENT_NAME, ProcessConfiguration.builder().build());
        assertLog(log, ".*result.ok: true.*");
        assertLog(log, ".*result.action: boo.*");
        assertLog(log, ".*result.k: v.*");
        assertLog(log, ".*resultAction: boo.*");
    }

    @Test
    public void testReentrantWithError() throws Exception {
        deploy("reentrantTaskWithError");
        save(ProcessConfiguration.builder()
                .putArguments("actionName", "boo")
                .putArguments("errorOnResume", true)
                .build());

        byte[] log = run();
        assertLog(log, ".*execute .*errorOnResume=true.*");

        log = resume(ReentrantTaskExample.EVENT_NAME, ProcessConfiguration.builder().build());
        assertLog(log, ".*error handled: java.lang.RuntimeException: Error on resume.*");
        assertLog(log, ".*process finished.*");
    }

    @Test
    public void testNestedSet() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("deep", 3);
        args.put("z", 234);

        deploy("nestedSet");
        save(ProcessConfiguration.builder()
                .putArguments("x", args)
                .build());

        byte[] log = run();
        assertLog(log, ".*x: .*y=123.*");
        assertLog(log, ".*x: .*z=234.*");
        assertLog(log, ".*x: .*taskOut=42.*");
        assertLog(log, ".*x: .*taskOut2=165.*");
        assertLog(log, ".*x: .*fromArgs=234.*");
        assertLog(log, ".*x: .*deep=\\{beep=1\\}.*");
        assertLog(log, ".*a: 42.*");
        assertLog(log, ".*a2: 1.*");
    }

    @Test
    public void testSetMapVariableOverride() throws Exception {
        deploy("setVariableOverride");
        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*myMap1: .*\\{y=2\\}.*");
        assertLog(log, ".*myMap2: .*\\{y=2, z=3\\}.*");
        assertLog(log, ".*myMap3: .*\\{z=4\\}.*");
        assertLog(log, ".*myMap4: .*\\{k=v\\}.*");
    }

    @Test
    public void testRetry() throws Exception {
        deploy("retry");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*faultyOnceTask: fail.*");
        assertLog(log, ".*Waiting for 1000ms.*");
        assertLog(log, ".*faultyOnceTask: ok.*");
        assertLog(log, ".*neverFailTask: ok.*");
    }

    @Test
    public void testRetryInput() throws Exception {
        deploy("retryInput");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLogAtLeast(log, 1, ".*ConditionallyFailTask: fail.*");
        assertLog(log, ".*Waiting for 1000ms.*");
        assertLog(log, ".*ConditionallyFailTask: ok.*");
    }

    @Test
    public void testCheckpointExpr() throws Exception {
        deploy("checkpointExpr");

        save(ProcessConfiguration.builder()
                .putArguments("x", 123)
                .build());

        run();
        verify(checkpointService, times(1)).upload(any(), any(), eq("test_123"), any());
    }

    @Test
    public void testCheckpointRestore() throws Exception {
        deploy("checkpointRestore");

        save(ProcessConfiguration.builder()
                .putArguments("aVar", Collections.singletonMap("x", 123))
                .build());

        run();

        verify(checkpointService, times(1)).upload(any(), any(), eq("first"), any());
        verify(checkpointService, times(1)).upload(any(), any(), eq("second"), any());

        checkpointService.restore("first", workDir);

        run();

        assertLogAtLeast(allLogs, 2, ".*#3.*x=124.*");
        assertLogAtLeast(allLogs, 2, ".*#3.*y=345.*");

        assertLog(allLogs, ".*Event Name: first.*");
    }

    @Test
    public void testCheckpoint1_93_0Restore() throws Exception {
        deploy("checkpointRestore2");

        save(ProcessConfiguration.builder()
                .build());

        checkpointService.put("first", Paths.get(MainTest.class.getResource("checkpointRestore2/first_1.103.1.zip").toURI()));
        checkpointService.restore("first", workDir);

        run();

        assertLog(allLogs, ".*item: one.*");
        assertLog(allLogs, ".*item: two.*");
    }

    @Test
    public void testNullIfExpression() throws Exception {
        deploy("ifExpressionAsNull");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", Collections.singletonMap("x", 123))
                .build());

        byte[] log = run();
        assertLog(log, ".*it's null.*");
    }

    @Test
    public void testTaskOut() throws Exception {
        deploy("taskOut");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("single out x.ok=true") + ".*");
        assertLog(log, ".*" + quote("single out x.k=some-value") + ".*");
        assertLog(log, ".*" + quote("expression out x=some-value") + ".*");
    }

    @Test
    public void testTaskOutWithItems() throws Exception {
        deploy("taskOutWithItems");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("single out x=[10, 20, 30]") + ".*");
    }

    @Test
    public void testExprOutExpression() throws Exception {
        deploy("exprOutExpr");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: v.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testFormsParallel() throws Exception {
        deploy("parallelForm");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*Before parallel.*");

        List<Form> forms = formService.list();
        assertEquals(2, forms.size());

        Form form1 = forms.stream()
                .filter(f -> "form1".equals(f.name())).findFirst()
                .orElseThrow(() -> new RuntimeException("form not found"));

        // resume the process using the saved form

        log = resume(form1.eventName(), ProcessConfiguration.builder()
                .arguments(Collections.singletonMap("form1", Collections.singletonMap("firstName", "Vasia")))
                .build());
        assertLog(log, ".*form1 in block: Vasia.*");

        // resume the process using the saved form

        Form form2 = formService.list().stream()
                .filter(f -> "form2".equals(f.name())).findFirst()
                .orElseThrow(() -> new RuntimeException("form not found"));
        log = resume(form2.eventName(), ProcessConfiguration.builder()
                .arguments(Collections.singletonMap("form2", Collections.singletonMap("firstName", "Pupkin")))
                .build());
        assertLog(log, ".*form2: Pupkin.*");
        assertLog(log, ".*form1: Vasia.*");
    }

    @Test
    public void testContextInjector() throws Exception {
        deploy("injectorTest");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*done!.*");
    }

    @Test
    public void testContextInjectorWithSegmentedLogger() throws Exception {
        deploy("injectorTest");

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .build())
                .build();

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run(runnerCfg);

        assertLog(log, Pattern.quote("|0|1|1|0|0||70|2|0|0|0|") + ".*done!.*");
        assertLog(log, Pattern.quote("|0|2|1|0|0|"));
    }

    @Test
    public void testCurrentFlowName() throws Exception {
        deploy("currentFlowName");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*default: default.*");
        assertLog(log, ".*myFlow: myFlow.*");

        checkpointService.restore("first", workDir);

        run();

        assertLogAtLeast(allLogs, 2, ".*after checkpoint: default.*");
        assertLogAtLeast(allLogs, 2, ".*current flow name in error block: default.*");
    }

    @Test
    public void testEvalAsMap() throws Exception {
        deploy("evalAsMap");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x:.*a.out3=\\$\\{a.out1}.*");
        assertLog(log, ".*x:.*a.out1=1.*");
        assertLog(log, ".*x:.*a.out2=2.*");

        assertLog(log, ".*eval: \\{a=\\{.*");
        assertLog(log, ".*1: 1.*");
        assertLog(log, ".*2: 2.*");
        assertLog(log, ".*3: 1.*");
    }

    @Test
    public void testOrDefaultFunction() throws Exception {
        deploy("orDefault");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x:.*defaultValue.*");

        // ---
        save(ProcessConfiguration.builder()
                .putArguments("x", "x-value")
                .build());

        log = run();
        assertLog(log, ".*x:.*x-value.*");
    }

    @Test
    public void testIsDebug() throws Exception {
        deploy("isDebug");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*isDebug: false.*");

        save(ProcessConfiguration.builder()
                .debug(true)
                .build());

        log = run();
        assertLog(log, ".*isDebug: true.*");
    }

    @Test
    public void argsFromArgs() throws Exception {
        deploy("argsFromArgs");

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("k1", "v1");
        args.put("k2", "${resultTask.get('args.k1')}");

        save(ProcessConfiguration.builder()
                .putArguments("args", args)
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, " + Pattern.quote("{k1=v1, k2=v1}") + ".*");
    }

    @Test
    @IgnoreSerializationAssert
    public void loopItemSerialization() throws Exception {
        deploy("loopSerializationError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*name=one.*");
    }

    @Test
    public void testThrowExpression() throws Exception {
        deploy("throwExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("exception expected");
        } catch (UserDefinedException e) {
            assertEquals("42 not found", e.getMessage());
        }
    }

    @Test
    public void testSensitiveData() throws Exception {
        deploy("sensitiveData");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("sensitive: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("log value: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("hack: B O O M") + ".*");

        assertLog(log, ".*" + Pattern.quote("map: {nonSecretButMasked=******, secret=******}") + ".*");
        assertLog(log, ".*" + Pattern.quote("map: {nonSecret=non secret value, secret=******}") + ".*");

        assertLog(log, ".*" + Pattern.quote("plain: plain") + ".*");

        assertLog(log, ".*" + Pattern.quote("secret from map: ******") + ".*");

        log = resume("ev1", ProcessConfiguration.builder().build());
        assertLog(log, ".*" + Pattern.quote("mySecret after suspend: ******") + ".*");
    }

    @Test
    public void testIncVariable() throws Exception {
        deploy("incVariable");

        save(ProcessConfiguration.builder()
                .putArguments("counter", 0)
                .build());

        byte[] log = run();
        assertLog(log, ".*counter: 1.*");
    }

    @Test
    public void testHasNonNullVariable() throws Exception {
        deploy("hasNonNullVariable");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*true == true.*");
        assertLog(log, ".*false == false.*");
    }

    @Test
    public void testInvalidExpressionError() throws Exception {
        deploy("invalidExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String logString = new String(lastLog);
        String expected = "[ERROR] (concord.yml): Error @ line: 9, col: 7. Error Parsing: ${str.split('\\n')}. Encountered \"\\'\\\\n\" at line 1, column 13.\n" +
                "Was expecting one of:\n" +
                "    \"{\" ...\n" +
                "    <INTEGER_LITERAL> ...\n" +
                "    <FLOATING_POINT_LITERAL> ...\n" +
                "    <STRING_LITERAL> ...\n" +
                "    \"true\" ...\n" +
                "    \"false\" ...\n" +
                "    \"null\" ...\n" +
                "    \"(\" ...\n" +
                "    \")\" ...\n" +
                "    \"[\" ...\n" +
                "    \"!\" ...\n" +
                "    \"not\" ...\n" +
                "    \"empty\" ...\n" +
                "    \"-\" ...\n" +
                "    <IDENTIFIER> ...";

        assertTrue(logString.contains(expected), "expected log contains: " + expected + ", actual: " + logString);
    }

    @Test
    public void testNPEInExpression() throws Exception {
        deploy("npeInExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String logString = new String(lastLog);
        String expected = "[ERROR] (concord.yml): Error @ line: 7, col: 7. while evaluating expression '${'a' += m.n += 'b'}': ";

        assertTrue(logString.contains(expected), "expected log contains: " + expected + ", actual: " + logString);
    }

    @Test
    public void testExpressionThrowUserDefinedError() throws Exception {
        deploy("faultyExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(lastLog, ".*" + Pattern.quote("[ERROR] (concord.yml): Error @ line: 3, col: 7. BOOM"));
    }

    @Test
    public void testExpressionThrowException() throws Exception {
        deploy("exceptionFromExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String expected = "[ERROR] (concord.yml): Error @ line: 3, col: 7. while evaluating expression '${faultyTask.exception('BOOM')}': javax.el.ELException: java.lang.Exception: BOOM";
        assertLog(lastLog, ".*" + Pattern.quote(expected));
    }

    @Test
    public void testTaskThrowUserDefinedError() throws Exception {
        deploy("faultyTask2");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(lastLog, ".*" + Pattern.quote("[ERROR] (concord.yml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
    }

    @Test
    public void testTaskThrowRuntimeException() throws Exception {
        deploy("faultyTask3");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(lastLog, ".*" + Pattern.quote("[ERROR] (concord.yml): Error @ line: 3, col: 7. boom!"));
    }

    @Test
    public void testTaskThrowException() throws Exception {
        deploy("faultyTask4");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(lastLog, ".*" + Pattern.quote("[ERROR] (concord.yml): Error @ line: 3, col: 7. boom!"));
    }

    @Test
    public void testUnresolvedVarInStepName() throws Exception {
        deploy("unresolvedVarInStepName");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("exception expected");
        } catch (Exception e) {
        }

        assertLog(lastLog, ".*" + quote("(concord.yml): Error @ line: 4, col: 7. Can't find a variable 'undefined' used in '${undefined}'") + ".*");
    }

    @Test
    public void testUnresolvedVarInLoop() throws Exception {
        deploy("unresolvedVarInLoop");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("exception expected");
        } catch (Exception e) {
        }

        assertLog(lastLog, ".*" + quote("(concord.yml): Error @ line: 6, col: 7. Can't find a variable 'undefined' used in '${undefined}'") + ".*");
    }

    @Test
    public void testUnresolvedVarInRetry() throws Exception {
        deploy("unresolvedVarInRetry");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("exception expected");
        } catch (Exception e) {
        }

        assertLog(lastLog, ".*" + quote("(concord.yml): Error @ line: 6, col: 7. Can't find a variable 'undefined' used in '${undefined}'") + ".*");
    }


    @Test
    public void testArrayEvalSerialize() throws Exception {
        deploy("lazyEvalMapInArgs");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("{dev=dev-cloud1}, {prod=prod-cloud1}, {test=test-cloud1}, {perf=perf-cloud2}, {ci=perf-ci}") + ".*");
    }

    @Test
    public void testEntrySetSerialization() throws Exception {
        deploy("entrySetSerialization");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*myList: \\[k=v\\].*");
    }

    @Test
    public void testHasFlow() throws Exception {
        deploy("hasFlow");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*123: false.*");
        assertLog(log, ".*myFlow: true.*");
    }

    @Test
    public void testUuidFunc() throws Exception {
        deploy("uuid");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*uuid: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");
    }

    @Test
    public void testExitFromParallelLoop() throws Exception {
        deploy("parallelLoopExit");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();

        assertNoLog(log, ".*should not reach here.*");
    }

    @Test
    public void testExitFromSerialLoop() throws Exception {
        deploy("serialLoopExit");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();

        assertNoLog(log, ".*should not reach here.*");

        assertLog(log, ".*inner start: one.*");
        assertLog(log, ".*inner end: one.*");
        assertLog(log, ".*inner start: two.*");

        assertNoLog(log, ".*inner end: two.*");
        assertNoLog(log, ".*inner start: three.*");
        assertNoLog(log, ".*inner start: four.*");
    }

    @Test
    public void testStringIfExpression() throws Exception {
        deploy("ifExpressionAsString");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", Collections.singletonMap("str", "true"))
                .build());

        byte[] log = run();
        assertLog(log, ".*it's true.*");
    }

    @Test
    public void testParallelLoopItemIndex() throws Exception {
        deploy("parallelLoopItemIndex");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*serial: five==5.*");
        assertLog(log, ".*serial: four==4.*");
        assertLog(log, ".*serial: three==3.*");
        assertLog(log, ".*serial: two==2.*");
        assertLog(log, ".*serial: one==1.*");

        assertLog(log, ".*parallel: five==5.*");
        assertLog(log, ".*parallel: four==4.*");
        assertLog(log, ".*parallel: three==3.*");
        assertLog(log, ".*parallel: two==2.*");
        assertLog(log, ".*parallel: one==1.*");
    }

    private void deploy(String resource) throws URISyntaxException, IOException {
        Path src = Paths.get(MainTest.class.getResource(resource).toURI());
        IOUtils.copy(src, workDir);
    }

    private void save(ProcessConfiguration cfg) {
        ImmutableProcessConfiguration.Builder b = ProcessConfiguration.builder().from(cfg)
                .instanceId(instanceId);

        if (cfg.entryPoint() == null) {
            b.entryPoint(Constants.Request.DEFAULT_ENTRY_POINT_NAME);
        }

        this.processConfiguration = b.build();
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
        assertNotNull(processConfiguration, "save() the process configuration first");

        ImmutableRunnerConfiguration.Builder runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder().segmentedLogs(false).build());

        if (baseCfg != null) {
            runnerCfg.from(baseCfg);
        }

        runnerCfg.agentId(UUID.randomUUID().toString())
                .api(ApiConfiguration.builder()
                        .baseUrl("http://localhost:8001") // TODO make optional?
                        .build());

        PrintStream oldOut = System.out;

        ByteArrayOutputStream logStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(logStream);
        System.setOut(out);

        AbstractModule runtimeModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(DefaultTaskVariablesService.class).toProvider(new DefaultTaskVariablesProvider(processConfiguration));
                bind(RunnerLogger.class).toProvider(LoggerProvider.class);
                bind(LoggingClient.class).to(TestLoggingClient.class);
            }
        };

        byte[] log;
        try {
            Injector injector = new InjectorFactory(new WorkingDirectory(workDir),
                    runnerCfg.build(),
                    () -> processConfiguration,
                    testServices,
                    runtimeModule)
                    .create();
            injector.getInstance(Main.class).execute();
        } finally {
            out.flush();
            System.setOut(oldOut);

            log = logStream.toByteArray();
            System.out.write(log, 0, log.length);

            lastLog = log;
        }

        if (allLogs == null) {
            allLogs = log;
        } else {
            // append the current log to allLogs
            ByteArrayOutputStream baos = new ByteArrayOutputStream(allLogs.length + log.length);
            baos.write(allLogs);
            baos.write(log);
            allLogs = baos.toByteArray();
        }

        return log;
    }

    private static void assertLogAtLeast(byte[] ab, int n, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        if (grep(ab, pattern) < n) {
            fail("Expected at least " + n + " log line(s): " + pattern + ", got: \n" + new String(ab));
        }
    }

    private static void assertLog(byte[] ab, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        if (grep(ab, pattern) != 1) {
            fail("Expected a single log line: " + pattern + ", got: \n" + new String(ab));
        }
    }

    private static void assertNoLog(byte[] ab, String pattern) throws IOException {
        if (grep(ab, pattern) > 0) {
            fail("Expected no log lines like this: " + pattern + ", got: \n" + new String(ab));
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

        private final Variables defaults;

        @Inject
        public TestDefaults(Context ctx) {
            this.defaults = ctx.defaultVariables();
        }

        @Override
        public TaskResult execute(Variables input) {
            System.out.println("defaultsMap:" + defaults.toMap());
            return TaskResult.success();
        }

        @Value.Immutable
        @Value.Style(jdkOnly = true)
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
        public TaskResult execute(Variables input) {
            return TaskResult.success()
                    .value("expression", "${" + input.get("expression") + "}");
        }
    }

    @Named("testTask")
    @SuppressWarnings("unused")
    static class TestTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success()
                    .values(input.toMap());
        }
    }

    @Named("resultTask")
    @SuppressWarnings("unused")
    public static class ResultTask implements Task {

        private final Context context;

        @Inject
        public ResultTask(Context context) {
            this.context = context;
        }

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success()
                    .value("result", input.get("result"));
        }

        public Object get(String path) {
            String[] p = path.split("\\.");
            Map<String, Object> m = context.variables().getMap(p[0], Collections.emptyMap());
            p = Arrays.copyOfRange(p, 1, p.length);
            return ConfigurationUtils.get(m, p);
        }
    }

    @Named("loggingExample")
    @SuppressWarnings("unused")
    static class LoggingExampleTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(LoggingExampleTask.class);
        private static final Logger processLog = LoggerFactory.getLogger("processLog");

        @Override
        public TaskResult execute(Variables input) throws Exception {
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

            return TaskResult.success();
        }
    }

    @Named("unknownMethod")
    @SuppressWarnings("unused")
    static class UnknownMethodTask implements Task {

        public String sayHello() {
            return "Hello!";
        }
    }

    @Named("faultyTask")
    @SuppressWarnings("unused")
    public static class FaultyTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.fail("boom!")
                    .value("key", "value");
        }

        public void fail(String msg) {
            throw new UserDefinedException(msg);
        }

        public void exception(String msg) throws Exception {
            throw new Exception(msg);
        }
    }

    @Named("faultyTask2")
    @SuppressWarnings("unused")
    static class FaultyTask2 implements Task {

        @Override
        public TaskResult execute(Variables input) {
            throw new RuntimeException("boom!");
        }
    }

    @Named("faultyTask3")
    @SuppressWarnings("unused")
    static class FaultyTask3 implements Task {

        @Override
        public TaskResult execute(Variables input) throws Exception {
            throw new Exception("boom!");
        }
    }

    @Named("faultyOnceTask")
    @SuppressWarnings("unused")
    static class FaultyOnceTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(FaultyOnceTask.class);

        private static final AtomicBoolean toggle = new AtomicBoolean(false);

        @Override
        public TaskResult execute(Variables input) {
            if (!toggle.getAndSet(true)) {
                log.info("faultyOnceTask: fail");
                throw new RuntimeException("boom!");
            }

            log.info("faultyOnceTask: ok");
            return TaskResult.success();
        }
    }

    @Named("neverFailTask")
    @SuppressWarnings("unused")
    static class NeverFailTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(NeverFailTask.class);

        @Override
        public TaskResult execute(Variables input) {
            log.info("neverFailTask: ok");
            return TaskResult.success();
        }
    }

    @Named("conditionallyFailTask")
    @SuppressWarnings("unused")
    static class ConditionallyFailTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(NeverFailTask.class);

        @Override
        public TaskResult execute(Variables input) {
            if (input.getBoolean("fail", false)) {
                log.info("ConditionallyFailTask: fail");
                throw new RuntimeException("boom!");
            }

            log.info("ConditionallyFailTask: ok");

            return TaskResult.success();
        }
    }

    @Named("reentrantTask")
    @SuppressWarnings("unused")
    static class ReentrantTaskExample implements ReentrantTask {

        private static final Logger log = LoggerFactory.getLogger(ReentrantTaskExample.class);

        public static String EVENT_NAME = UUID.randomUUID().toString();

        @Override
        public TaskResult execute(Variables input) {
            log.info("execute {}", input.toMap());

            HashMap<String, Serializable> payload = new HashMap<>();
            payload.put("k", "v");
            payload.put("action", input.assertString("action"));
            payload.put("errorOnResume", input.getBoolean("errorOnResume", false));

            return TaskResult.reentrantSuspend(EVENT_NAME, payload);
        }

        @Override
        public TaskResult resume(ResumeEvent event) {
            log.info("RESUME: {}", event);
            if ((boolean) event.state().get("errorOnResume")) {
                throw new RuntimeException("Error on resume!");
            }

            return TaskResult.success()
                    .values((Map) event.state());
        }
    }

    @Named("simpleMethodTask")
    @SuppressWarnings("unused")
    public static class SimpleMethodTask implements Task {

        public int getValue() {
            return 42;
        }

        public int getDerivedValue(int value) {
            return value + 42;
        }
    }

    @Named("sensitiveTask")
    public static class TaskWithSensitiveData extends AbstractMap<String, String> implements Task {

        @SensitiveData
        public String getSensitive(String str) {
            return str;
        }

        @SensitiveData
        public Map<String, String> getSensitiveMap(String str) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("nonSecretButMasked", "some value");
            result.put("secret", str);
            return result;
        }

        @SensitiveData(keys = {"secret"})
        public Map<String, String> getSensitiveMapStrict(String str) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("nonSecret", "non secret value");
            result.put("secret", str);
            return result;
        }

        public String getPlain(String str) {
            return str;
        }

        @Override
        @SensitiveData
        public String get(Object key) {
            return key + "-value";
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return null;
        }
    }

    @Singleton
    static class TestLoggingClient implements LoggingClient {

        private final AtomicLong id = new AtomicLong(1L);

        @Override
        public long createSegment(UUID correlationId, String name) {
            return id.getAndIncrement();
        }
    }

    @Named("injectorTestBean")
    static class InjectorTestBean {

        private final Context ctx;

        @Inject
        public InjectorTestBean(Context ctx) {
            this.ctx = ctx;
        }
    }

    @Named("injectorTestTask")
    static class InjectorTestTask implements Task {

        private final Map<String, InjectorTestBean> testBeans;

        @Inject
        public InjectorTestTask(Map<String, InjectorTestBean> testBeans) {
            this.testBeans = testBeans;
        }

        @Override
        public TaskResult execute(Variables input) {
            testBeans.forEach((k, v) -> v.ctx.workingDirectory());
            return TaskResult.success()
                    .value("x", testBeans.size());
        }
    }

    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IgnoreSerializationAssert {
    }
}
