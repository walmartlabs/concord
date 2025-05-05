package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.TimeProvider;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.ImmutableRunnerConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
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
import com.walmartlabs.concord.runtime.v2.runner.vm.ParallelExecutionException;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class TestRuntimeV2 implements BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger(TestRuntimeV2.class);

    protected Path workDir;
    protected UUID instanceId;
    protected FormService formService;
    protected ProcessConfiguration processConfiguration;

    protected ProcessStatusCallback processStatusCallback;
    protected Module testServices;

    protected TestCheckpointUploader checkpointService;

    protected byte[] lastLog;
    protected byte[] allLogs;

    private Class<?> testClass;

    private Class<? extends PersistenceService> persistenceServiceClass;

    private TestTimeProvider timeProvider;

    public TestRuntimeV2 withPersistenceService(Class<? extends PersistenceService> persistenceServiceClass) {
        this.persistenceServiceClass = persistenceServiceClass;
        return this;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        boolean ignoreSerializationAssert = context.getTestMethod()
                .filter(m -> m.getAnnotation(IgnoreSerializationAssert.class) != null)
                .isPresent();

        setUp(!ignoreSerializationAssert);

        this.testClass = context.getTestClass().orElseThrow(() -> new IllegalStateException("No test class found"));

        this.timeProvider = new TestTimeProvider();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        tearDown();
    }

    public Path workDir() {
        return workDir;
    }

    public FormService formService() {
        return formService;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public byte[] lastLog() {
        return lastLog;
    }

    public byte[] allLogs() {
        return allLogs;
    }

    public TestCheckpointUploader checkpointService() {
        return checkpointService;
    }

    public ProcessStatusCallback processStatusCallback() {
        return processStatusCallback;
    }

    public void setWorkDir(Path newWorkDir) {
        this.workDir = newWorkDir;
    }

    public TestTimeProvider timeProvider() {
        return timeProvider;
    }

    public void deploy(String resource) throws URISyntaxException, IOException {
        var res = testClass.getResource(resource);
        assertNotNull(res, "Resource not found: " + resource);

        Path src = Paths.get(res.toURI());
        IOUtils.copy(src, workDir);
    }

    public ImmutableProcessConfiguration.Builder cfgFromDeployment() throws IOException {
        for (String fileName : Constants.Files.PROJECT_ROOT_FILE_NAMES) {
            Path p = workDir.resolve(fileName);
            if (Files.exists(p)) {
                var result = new ProjectLoaderV2((imports, dest, listener) -> List.of()).loadFromFile(p);
                return ProcessConfiguration.builder()
                        .arguments(result.getProjectDefinition().configuration().arguments());
            }
        }

        return ImmutableProcessConfiguration.builder();
    }

    public void save(ProcessConfiguration cfg) {
        ImmutableProcessConfiguration.Builder b = ProcessConfiguration.builder().from(cfg)
                .instanceId(instanceId);

        if (cfg.entryPoint() == null) {
            b.entryPoint(Constants.Request.DEFAULT_ENTRY_POINT_NAME);
        }

        this.processConfiguration = b.build();
    }

    public byte[] resume(String eventName, ProcessConfiguration cfg) throws Exception {
        StateManager.saveResumeEvent(workDir, eventName); // TODO use interface
        if (cfg != null) {
            save(cfg);
        }
        return run();
    }

    public byte[] run() throws Exception {
        return run(null);
    }

    public byte[] run(RunnerConfiguration baseCfg) throws Exception {
        if (processConfiguration == null) {
            save(cfgFromDeployment().build());
        }

        ImmutableRunnerConfiguration.Builder runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(false)
                        .workDirMasking(false)
                        .build());

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
                    runtimeModule,
                    new TimeProviderModule(timeProvider))
                    .create();
            injector.getInstance(Main.class).execute();
        } catch (UserDefinedException | ParallelExecutionException e) { // see {@link com.walmartlabs.concord.runtime.v2.runner.Main#main}
            throw e;
        } catch (Throwable t) {
            t.printStackTrace(out);
            throw t;
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

    public static void assertLogAtLeast(byte[] ab, int n, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        if (grep(ab, pattern) < n) {
            fail("Expected at least " + n + " log line(s): " + pattern + ", got: \n" + new String(ab));
        }
    }

    public static void assertLogExactMatch(byte[] ab, int n, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        int count = grep(ab, pattern);
        if (count != n) {
            fail("Expected exactly " + n + " log line(s): " + pattern + ", but found: " + count + "\nLog content:\n" + new String(ab));
        }
    }

    public static void assertLog(byte[] ab, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        int cnt = grep(ab, pattern);
        if (cnt != 1) {
            fail("Expected a single log line: " + pattern + ", got (" + cnt + "): \n" + new String(ab));
        }
    }

    public static void assertMultiLineLog(byte[] ab, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        int cnt = grepMultiLine(ab, pattern);
        if (cnt != 1) {
            fail("Expected a single log line: " + pattern + ", got (" + cnt + "): \n" + new String(ab));
        }
    }

    public static void assertNoLog(byte[] ab, String pattern) throws IOException {
        if (grep(ab, pattern) > 0) {
            fail("Expected no log lines like this: " + pattern + ", got: \n" + new String(ab));
        }
    }

    public static int grep(byte[] ab, String pattern) throws IOException {
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

    public static int grepMultiLine(byte[] ab, String pattern) throws IOException {
        int cnt = 0;

        Pattern compiledPattern = Pattern.compile(pattern, Pattern.DOTALL);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(ab);
             BufferedReader reader = new BufferedReader(new InputStreamReader(bais))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            Matcher matcher = compiledPattern.matcher(content.toString());
            while (matcher.find()) {
                cnt++;
            }
        }

        return cnt;
    }

    private void setUp(boolean withSerializationAssert) throws IOException {
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

                bind(ClassLoader.class).annotatedWith(Names.named("runtime")).toInstance(testClass.getClassLoader());

                bind(CheckpointUploader.class).toInstance(checkpointService);
                bind(CheckpointService.class).to(DefaultCheckpointService.class);
                bind(DependencyManager.class).to(DefaultDependencyManager.class);
                bind(DockerService.class).to(DefaultDockerService.class);
                bind(FileService.class).to(DefaultFileService.class);
                bind(LockService.class).to(DefaultLockService.class);
                if (persistenceServiceClass != null) {
                    bind(PersistenceService.class).to(persistenceServiceClass);
                } else {
                    bind(PersistenceService.class).toInstance(mock(PersistenceService.class));
                }
                bind(ProcessStatusCallback.class).toInstance(processStatusCallback);
                bind(SecretService.class).to(DefaultSecretService.class);
                bind(ApiClient.class).toInstance(mock(ApiClient.class));

                Multibinder<TaskProvider> taskProviders = Multibinder.newSetBinder(binder(), TaskProvider.class);
                taskProviders.addBinding().to(TaskV2Provider.class);

                Multibinder<TaskCallListener> taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
                taskCallListeners.addBinding().to(TaskCallPolicyChecker.class);
                taskCallListeners.addBinding().to(TaskResultListener.class);

                Multibinder<ExecutionListener> executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
                executionListeners.addBinding().toInstance(new ExecutionListener() {
                    @Override
                    public void beforeProcessStart(Runtime runtime, State state) {
                        SensitiveDataHolder.getInstance().get().clear();
                    }
                });
                executionListeners.addBinding().to(StackTraceCollector.class);

                if (withSerializationAssert) {
                    executionListeners.addBinding().toInstance(new ExecutionListener() {
                        @Override
                        public Result afterCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
                            if (cmd instanceof BlockCommand
                                || cmd instanceof ParallelCommand) {
                                return ExecutionListener.super.afterCommand(runtime, vm, state, threadId, cmd);
                            }

                            assertTrue(isSerializable(state), "Non serializable state after: " + cmd);
                            return ExecutionListener.super.afterCommand(runtime, vm, state, threadId, cmd);
                        }
                    });
                }
            }
        };

        allLogs = null;
    }

    private void tearDown() throws IOException {
        if (workDir != null) {
            IOUtils.deleteRecursively(workDir);
        }

        processConfiguration = null;

        LoggingConfigurator.reset();
    }

    private static boolean isSerializable(Object o) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
            oos.writeObject(o);
        } catch (IOException e) {
            log.warn("Serialization error: {}", e.getMessage(), e);
            return false;
        }

        return true;
    }

    @Singleton
    static class TestLoggingClient implements LoggingClient {

        private final AtomicLong id = new AtomicLong(1L);

        @Override
        public long createSegment(UUID correlationId, String name) {
            return id.getAndIncrement();
        }
    }
}
