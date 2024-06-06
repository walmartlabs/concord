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
import com.walmartlabs.concord.runtime.common.FormService;
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
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;
import org.immutables.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public abstract class AbstractTest {

    private static final Logger log = LoggerFactory.getLogger(MainTest.class);

    protected Path workDir;
    protected UUID instanceId;
    protected FormService formService;
    protected ProcessConfiguration processConfiguration;

    protected ProcessStatusCallback processStatusCallback;
    protected Module testServices;

    protected TestCheckpointUploader checkpointService;

    protected byte[] lastLog;
    protected byte[] allLogs;

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
                        .filter(m -> m.getAnnotation(MainTest.IgnoreSerializationAssert.class) != null)
                        .isPresent();
                if (!ignoreSerializationAssert) {
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

    private static boolean isSerializable(Object o) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
            oos.writeObject(o);
        } catch (IOException e) {
            log.warn("Serialization error: {}", e.getMessage(), e);
            return false;
        }

        return true;
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (workDir != null) {
            IOUtils.deleteRecursively(workDir);
        }

        LoggingConfigurator.reset();
    }

    protected void deploy(String resource) throws URISyntaxException, IOException {
        Path src = Paths.get(MainTest.class.getResource(resource).toURI());
        IOUtils.copy(src, workDir);
    }

    protected void save(ProcessConfiguration cfg) {
        ImmutableProcessConfiguration.Builder b = ProcessConfiguration.builder().from(cfg)
                .instanceId(instanceId);

        if (cfg.entryPoint() == null) {
            b.entryPoint(Constants.Request.DEFAULT_ENTRY_POINT_NAME);
        }

        this.processConfiguration = b.build();
    }

    protected byte[] resume(String eventName, ProcessConfiguration cfg) throws Exception {
        StateManager.saveResumeEvent(workDir, eventName); // TODO use interface
        if (cfg != null) {
            save(cfg);
        }
        return run();
    }

    protected byte[] run() throws Exception {
        return run(null);
    }

    protected byte[] run(RunnerConfiguration baseCfg) throws Exception {
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
                bind(LoggingClient.class).to(MainTest.TestLoggingClient.class);
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

    protected static void assertLogAtLeast(byte[] ab, int n, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        if (grep(ab, pattern) < n) {
            fail("Expected at least " + n + " log line(s): " + pattern + ", got: \n" + new String(ab));
        }
    }

    protected static void assertLog(byte[] ab, String pattern) throws IOException {
        if (ab == null) {
            fail("Log is empty");
        }

        if (grep(ab, pattern) != 1) {
            fail("Expected a single log line: " + pattern + ", got: \n" + new String(ab));
        }
    }

    protected static void assertNoLog(byte[] ab, String pattern) throws IOException {
        if (grep(ab, pattern) > 0) {
            fail("Expected no log lines like this: " + pattern + ", got: \n" + new String(ab));
        }
    }

    protected static int grep(byte[] ab, String pattern) throws IOException {
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
            log.info("will fail with error");
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

    @Named("suspendTask")
    @SuppressWarnings("unused")
    static class SuspendTask implements Task {
        @Override
        public TaskResult execute(Variables input) {
            log.info("will suspend with event: '{}'", input.assertString("eventName"));

            return TaskResult.suspend(input.assertString("eventName"));
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

    @Named("sleepTask")
    @SuppressWarnings("unused")
    public static class SleepTask implements Task {

        @Override
        public TaskResult execute(Variables input) throws Exception {
            Thread.sleep(input.assertInt("ms"));
            return TaskResult.success();
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
