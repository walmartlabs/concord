package com.walmartlabs.concord.runner;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.project.NoopImportsNormalizer;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import com.walmartlabs.concord.runner.engine.EventConfiguration;
import com.walmartlabs.concord.runner.engine.ProcessErrorProcessor;
import com.walmartlabs.concord.runtime.common.ProcessHeartbeat;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.sdk.Task;
import com.walmartlabs.concord.sdk.UserDefinedException;
import io.takari.bpm.api.*;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Named
@Singleton
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String RESUME_MARKER = Constants.Files.RESUME_MARKER_FILE_NAME;
    private static final String SUSPEND_MARKER = Constants.Files.SUSPEND_MARKER_FILE_NAME;

    private final EngineFactory engineFactory;
    private final ApiClientFactory apiClientFactory;
    private final DefaultVariablesConverter variablesConverter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public Main(EngineFactory engineFactory, ApiClientFactory apiClientFactory, DefaultVariablesConverter variablesConverter) {
        this.engineFactory = engineFactory;
        this.apiClientFactory = apiClientFactory;
        this.variablesConverter = variablesConverter;
    }

    public void run(RunnerConfiguration runnerCfg, Path baseDir) throws Exception {
        log.debug("run -> working directory: {}", baseDir.toAbsolutePath());

        long t1 = System.currentTimeMillis();

        Path idPath = baseDir.resolve(Constants.Files.INSTANCE_ID_FILE_NAME);
        UUID instanceId = readInstanceId(idPath);
        ((ApiClientFactoryImpl)apiClientFactory).setTxId(instanceId);

        long t2 = System.currentTimeMillis();
        if (runnerCfg.debug()) {
            log.info("Spent {}ms waiting for the payload", (t2 - t1));
        }

        Map<String, Object> policy = readPolicyRules(baseDir);
        if (policy.isEmpty()) {
            PolicyEngineHolder.INSTANCE.setEngine(null);
        } else {
            PolicyEngineHolder.INSTANCE.setEngine(new PolicyEngine(objectMapper.convertValue(policy, PolicyEngineRules.class)));
        }

        // read the process configuration
        Map<String, Object> processCfg = readRequest(baseDir);
        processCfg = variablesConverter.convert(baseDir, processCfg);

        String sessionToken = getSessionToken(processCfg);
        ApiClient apiClient = apiClientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(sessionToken)
                .build());

        ProcessHeartbeat heartbeat = new ProcessHeartbeat(apiClient, instanceId, runnerCfg.api().maxNoHeartbeatInterval());
        heartbeat.start();

        ProcessApiClient processApiClient = new ProcessApiClient(runnerCfg, apiClient);

        processApiClient.updateStatus(instanceId, runnerCfg.agentId(), ProcessEntry.StatusEnum.RUNNING);

        CheckpointManager checkpointManager = new CheckpointManager(instanceId, processApiClient);

        long t3 = System.currentTimeMillis();
        if (runnerCfg.debug()) {
            log.info("Ready to start in {}ms", (t3 - t2));
        }

        executeProcess(instanceId.toString(), checkpointManager, baseDir, processCfg);
    }

    private void executeProcess(String instanceId, CheckpointManager checkpointManager, Path baseDir, Map<String, Object> processCfg) throws ExecutionException {
        // get active profiles from the request data
        Collection<String> activeProfiles = getActiveProfiles(processCfg);

        // load the project
        ProjectDefinition project = loadProject(baseDir);

        // read the list of metadata variables
        Set<String> metaVariables = getMetaVariables(processCfg);

        // event recording processCfg
        EventConfiguration eventCfg = getEventCfg(processCfg);

        boolean dryRunMode = MapUtils.getBoolean(processCfg, Constants.Request.DRY_RUN_MODE_KEY, false);
        if (dryRunMode) {
            throw new IllegalArgumentException("Dry run mode is not supported in runtime-v1");
        }

        Engine engine = engineFactory.create(project, baseDir, activeProfiles, metaVariables, eventCfg);

        Map<String, Object> resumeCheckpointReq = null;
        while (true) {
            Collection<Event> resultEvents;

            // check if we need to resume the process from a saved point
            Set<String> eventNames = StateManager.readResumeEvents(baseDir);
            if (eventNames == null || eventNames.isEmpty()) {
                // running fresh
                // let's check if there are some saved variables (e.g. from the parent process)
                Variables vars = readSavedVariables(baseDir);
                resultEvents = start(engine, vars, processCfg, instanceId, baseDir);
            } else {
                if (eventNames.size() > 1) {
                    throw new IllegalStateException("Runtime v1 supports resuming for only one event at the time. Got: " + eventNames);
                }

                String eventName = eventNames.iterator().next();
                resultEvents = resume(engine, processCfg, instanceId, baseDir, eventName);
            }

            Event checkpointEvent = resultEvents.stream()
                    .filter(e -> e.getPayload() instanceof Map)
                    .filter(e -> getCheckpointId(e) != null)
                    .findFirst()
                    .orElse(null);

            // found a checkpoint, resume the process immediately
            if (checkpointEvent != null) {
                checkpointManager.process(getCheckpointId(checkpointEvent), getCorrelationId(checkpointEvent), checkpointEvent.getName(), baseDir);
                // clear arguments
                if (resumeCheckpointReq == null) {
                    resumeCheckpointReq = new HashMap<>(processCfg);
                    resumeCheckpointReq.remove(Constants.Request.ARGUMENTS_KEY);
                }
                processCfg = resumeCheckpointReq;
            } else {
                // no checkpoints, stop the execution and wait for an external event
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRequest(Path baseDir) throws ExecutionException {
        Path p = baseDir.resolve(Constants.Files.CONFIGURATION_FILE_NAME);

        try (InputStream in = Files.newInputStream(p)) {
            return objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ExecutionException("Error while reading request data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPolicyRules(Path ws) throws ExecutionException {
        Path policyFile = ws.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME).resolve(Constants.Files.POLICY_FILE_NAME);
        if (!Files.exists(policyFile)) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(policyFile.toFile(), Map.class);
        } catch (IOException e) {
            throw new ExecutionException("Error while reading policy rules");
        }
    }

    @SuppressWarnings("BusyWait")
    private static UUID readInstanceId(Path src) {
        long errorCount = 0;
        while (true) {
            byte[] id = readIfExists(src);
            if (id != null && id.length > 0) {
                String s = new String(id);
                try {
                    return UUID.fromString(s.trim());
                } catch (IllegalArgumentException e) {
                    errorCount++;
                    if (errorCount % 10 == 0) {
                        log.warn("waitForInstanceId ['{}'] -> value: '{}', error: {}", src, s, e.getMessage());
                    }
                }
            }

            // we are not using WatchService as it has issues when running in Docker
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static UUID getCheckpointId(Event e) {
        String s = MapUtils.getString((Map<String, Object>) e.getPayload(), "checkpointId");
        if (s == null) {
            return null;
        }
        return UUID.fromString(s);
    }

    @SuppressWarnings("unchecked")
    private static UUID getCorrelationId(Event e) {
        String s = MapUtils.getString((Map<String, Object>) e.getPayload(), "correlationId");
        if (s == null) {
            return null;
        }
        return UUID.fromString(s);
    }

    private static Collection<Event> start(Engine e, Variables vars, Map<String, Object> req, String instanceId, Path baseDir) throws ExecutionException {
        // get the entry point
        String entryPoint = (String) req.get(Constants.Request.ENTRY_POINT_KEY);
        if (entryPoint == null) {
            throw new ExecutionException("Entry point must be set");
        }

        // prepare the process' arguments
        Map<String, Object> args = createArgs(instanceId, baseDir, req);

        Object initiator = req.get(Constants.Request.INITIATOR_KEY);
        if (initiator != null) {
            args.put(Constants.Request.INITIATOR_KEY, initiator);
            args.put(Constants.Request.CURRENT_USER_KEY, initiator);
        }

        // start the process
        log.debug("start ['{}', '{}'] -> entry point: {}, starting...", instanceId, baseDir, entryPoint);

        e.start(instanceId, entryPoint, vars, args);

        // save the suspended state marker if needed
        return finalizeState(e, instanceId, baseDir);
    }

    private static Collection<Event> resume(Engine e, Map<String, Object> req, String instanceId, Path baseDir, String eventName) throws ExecutionException {
        Map<String, Object> args = createArgs(instanceId, baseDir, req);

        Object currentUser = req.get(Constants.Request.CURRENT_USER_KEY);
        if (currentUser != null) {
            args.put(Constants.Request.CURRENT_USER_KEY, currentUser);
        }

        log.debug("resume ['{}', '{}', '{}'] -> resuming...", instanceId, baseDir, eventName);

        // start the process
        e.resume(instanceId, eventName, args);

        // save the suspended state marker if needed
        return finalizeState(e, instanceId, baseDir);
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getActiveProfiles(Map<String, Object> cfg) {
        if (cfg == null) {
            return Collections.emptyList();
        }

        Object v = cfg.get(Constants.Request.ACTIVE_PROFILES_KEY);
        if (v == null) {
            return Collections.emptyList();
        }

        return (Collection<String>) v;
    }

    private static ProjectDefinition loadProject(Path baseDir) throws ExecutionException {
        try {
            // assume all imports were processed by the agent
            return new ProjectLoader(new NoopImportManager())
                    .loadProject(baseDir, new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER)
                    .getProjectDefinition();
        } catch (Exception e) {
            throw new ExecutionException("Error while loading a project", e);
        }
    }

    private static Variables readSavedVariables(Path baseDir) {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        Path varsFile = stateDir.resolve(Constants.Files.LAST_KNOWN_VARIABLES_FILE_NAME);
        if (!Files.exists(varsFile)) {
            return null;
        }

        Variables vars;
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(varsFile))) {
            vars = (Variables) in.readObject();
        } catch (ClassNotFoundException | IOException e) {
            log.error("Can't restore the saved variables: {}", e.getMessage());
            return null;
        }

        // if a process error was successfully handled by the engine, it will be saved as a `lastError` variable
        // we should't overwrite it with the Agent's version
        if (vars.hasVariable(Constants.Context.LAST_ERROR_KEY)) {
            return vars;
        }

        Path lastErrorFile = stateDir.resolve(Constants.Files.LAST_ERROR_FILE_NAME);
        if (!Files.exists(lastErrorFile)) {
            return vars;
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(lastErrorFile))) {
            Throwable t = (Throwable) in.readObject();
            vars = vars.setVariable(Constants.Context.LAST_ERROR_KEY, t);
        } catch (ClassNotFoundException | IOException e) {
            log.error("Can't restore the saved last error variable: {}", e.getMessage());
            return vars;
        }

        return vars;
    }

    @SuppressWarnings({"unchecked"})
    private static Map<String, Object> createArgs(String instanceId, Path workDir, Map<String, Object> cfg) {
        Map<String, Object> m = new LinkedHashMap<>();

        // original arguments
        Map<String, Object> args = (Map<String, Object>) cfg.get(Constants.Request.ARGUMENTS_KEY);
        if (args != null) {
            m.putAll(args);
        }

        // instance ID
        m.put(Constants.Context.TX_ID_KEY, instanceId);

        // workDir
        String dir = workDir.toAbsolutePath().toString();
        m.put(Constants.Context.WORK_DIR_KEY, dir);

        // out variables
        Object outExpr = cfg.get(Constants.Request.OUT_EXPRESSIONS_KEY);
        if (outExpr != null) {
            m.put(Constants.Context.OUT_EXPRESSIONS_KEY, outExpr);
        }

        // processInfo
        Map<String, Object> processInfo = (Map<String, Object>) cfg.get(Constants.Request.PROCESS_INFO_KEY);
        if (processInfo != null) {
            m.put(Constants.Request.PROCESS_INFO_KEY, processInfo);
        }

        // projectInfo
        Map<String, Object> projectInfo = (Map<String, Object>) cfg.get(Constants.Request.PROJECT_INFO_KEY);
        if (projectInfo != null) {
            m.put(Constants.Request.PROJECT_INFO_KEY, projectInfo);
        }

        return m;
    }

    @SuppressWarnings("unchecked")
    private Set<String> getMetaVariables(Map<String, Object> cfg) {
        Map<String, Object> meta = (Map<String, Object>) cfg.get(Constants.Request.META);
        if (meta != null) {
            return meta.keySet();
        }
        return Collections.emptySet();
    }

    private EventConfiguration getEventCfg(Map<String, Object> cfg) {
        Map<String, Object> m = MapUtils.getMap(cfg, "runner", Collections.emptyMap());
        m = MapUtils.getMap(m, "events", Collections.emptyMap());
        return objectMapper.convertValue(m, EventConfiguration.class);
    }

    private static Collection<Event> finalizeState(Engine engine, String instanceId, Path baseDir) throws ExecutionException {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        try {
            Path resumeMarker = stateDir.resolve(RESUME_MARKER);
            if (Files.exists(resumeMarker)) {
                Files.delete(resumeMarker);
            }

            Path suspendMarker = stateDir.resolve(SUSPEND_MARKER);
            if (Files.exists(suspendMarker)) {
                Files.delete(suspendMarker);
            }
        } catch (IOException e) {
            throw new ExecutionException("State cleanup error", e);
        }

        EventService es = engine.getEventService();
        Collection<Event> events = es.getEvents(instanceId);

        try {
            if (events.isEmpty()) {
                IOUtils.deleteRecursively(stateDir);
                log.debug("finalizeState ['{}'] -> removed the state", instanceId);
            } else {
                if (!Files.exists(stateDir)) {
                    Files.createDirectories(stateDir);
                }

                Set<String> eventNames = events.stream().map(Event::getName).collect(Collectors.toSet());

                Path marker = stateDir.resolve(SUSPEND_MARKER);
                Files.write(marker, eventNames);
                log.debug("finalizeState ['{}'] -> created the suspended marker", instanceId);
            }
            return events;
        } catch (IOException e) {
            throw new ExecutionException("State saving error", e);
        }
    }

    public static void main(String[] args) {
        // determine current working directory, it should contain the payload
        Path baseDir = Paths.get(System.getProperty("user.dir"));

        try {
            long t1 = System.currentTimeMillis();

            // load the config
            RunnerConfiguration runnerCfg = validate(loadRunnerCfg(args));
            // TODO enable security manager

            // load dependencies
            List<URL> deps = loadDependencyList(runnerCfg);
            if (runnerCfg.debug()) {
                log.info("Effective dependencies:\n\t{}", deps.stream()
                        .map(URL::toString)
                        .collect(Collectors.joining("\n\t")));
            }

            URLClassLoader depsClassLoader = new URLClassLoader(deps.toArray(new URL[0]), Main.class.getClassLoader()); // NOSONAR
            Thread.currentThread().setContextClassLoader(depsClassLoader);

            // create the injector to wire up and initialize all dependencies
            Injector injector = createInjector(runnerCfg, depsClassLoader, baseDir);

            Main main = injector.getInstance(Main.class);

            long t2 = System.currentTimeMillis();
            if (runnerCfg.debug()) {
                log.info("Runtime loaded in {}ms", (t2 - t1));
            }

            main.run(runnerCfg, baseDir);

            // force exit (helps with runaway threads)
            System.exit(0);
        } catch (Throwable e) { // catch both errors and exceptions
            // try to unroll nested exceptions to get a meaningful one
            Throwable t = unroll(e);

            if (t instanceof UserDefinedException) {
                log.error("{}", t.getMessage());
            } else {
                log.error("main -> unhandled exception", t);
            }
            saveLastError(baseDir, t);
            System.exit(1);
        }
    }

    private static RunnerConfiguration loadRunnerCfg(String[] args) throws IOException {
        if (args.length < 1) {
            return RunnerConfiguration.builder().build();
        }

        Path cfgFile = Paths.get(args[0]);
        try (InputStream in = Files.newInputStream(cfgFile)) {
            return createObjectMapper().readValue(in, RunnerConfiguration.class);
        }
    }

    // TODO bind the same ObjectMapper instance into the injector?
    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());
        return om;
    }

    private static RunnerConfiguration validate(RunnerConfiguration cfg) {
        if (cfg.agentId() == null) {
            throw new IllegalArgumentException("Configuration parameter 'agentId' is required");
        }

        if (cfg.api() == null || cfg.api().baseUrl() == null) {
            throw new IllegalArgumentException("Configuration parameter 'api.baseUrl' is required");
        }

        return cfg;
    }

    private static List<URL> loadDependencyList(RunnerConfiguration cfg) throws Exception {
        if (cfg.dependencies() == null) {
            return Collections.emptyList();
        }

        return parseDeps(cfg.dependencies());
    }

    private static String getSessionToken(Map<String, Object> cfg) {
        Map<String, Object> processInfo = MapUtils.getMap(cfg, Constants.Request.PROCESS_INFO_KEY, Collections.emptyMap());
        String s = MapUtils.getString(processInfo, "sessionKey");
        if (s == null) {
            throw new IllegalStateException("Can't find 'processInfo.sessionKey' in the process configuration");
        }
        return s;
    }

    private static Throwable unroll(Throwable e) {
        if (e instanceof ExecutionException) {
            if (e.getCause() != null) {
                e = e.getCause();
            }
        }

        if (e instanceof BpmnError) {
            if (e.getCause() != null) {
                e = e.getCause();
            }
        }

        if (e instanceof javax.el.ELException) {
            if (e.getCause() != null) {
                e = e.getCause();
            }
        }

        return e;
    }

    private static List<URL> parseDeps(Collection<String> deps) throws IOException {
        List<URL> result = deps.stream()
                .sorted()
                .map(s -> {
                    if (!Files.exists(Paths.get(s))) {
                        throw new RuntimeException("Dependency file: " + s + " not found");
                    }

                    try {
                        return new URL("file://" + s);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Invalid dependency path " + s + ":" + e.getMessage());
                    }
                }).collect(Collectors.toList());

        // payload's own libraries are stored in `./lib/` directory in the working directory
        Path baseDir = Paths.get(System.getProperty("user.dir"));
        Path lib = baseDir.resolve(Constants.Files.LIBRARIES_DIR_NAME);
        if (Files.exists(lib)) {
            try (Stream<Path> s = Files.list(lib)) {
                s.forEach(f -> {
                    if (f.toString().endsWith(".jar")) {
                        try {
                            result.add(f.toUri().toURL());
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }

        return result;
    }

    private static Injector createInjector(RunnerConfiguration runnerCfg, ClassLoader depsClassLoader, Path workDir) {
        ClassLoader cl = Main.class.getClassLoader();

        Module cfg = new AbstractModule() {
            @Override
            protected void configure() {
                bind(RunnerConfiguration.class).toInstance(runnerCfg);
                bind(WorkingDirectory.class).toInstance(new WorkingDirectory(workDir));
            }
        };

        Module tasks = new AbstractModule() {
            @Override
            protected void configure() {
                TaskClasses taskClassesHolder = new TaskClasses();
                bindListener(new SubClassesOf(Task.class), new TaskClassesListener(taskClassesHolder, false));
                bind(TaskClasses.class).toInstance(taskClassesHolder);
            }
        };

        Module taskCallModule = new AbstractModule() {
            @Override
            protected void configure() {
                install(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bindInterceptor(
                                TaskCallInterceptor.CLASS_MATCHER,
                                TaskCallInterceptor.METHOD_MATCHER,
                                new TaskCallInterceptor(PolicyEngineHolder.INSTANCE));
                    }
                });
            }
        };

        Module m = new WireModule(
                cfg,
                tasks,
                taskCallModule,
                new SpaceModule(new URLClassSpace(cl), runnerCfg.enableSisuIndex() ? BeanScanning.GLOBAL_INDEX : BeanScanning.CACHE),
                new SpaceModule(new URLClassSpace(depsClassLoader), runnerCfg.enableSisuIndex() ? BeanScanning.GLOBAL_INDEX : BeanScanning.CACHE));

        return Guice.createInjector(m);
    }

    private static void saveLastError(Path baseDir, Throwable t) {
        Path attachmentsDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        Path stateDir = attachmentsDir.resolve(Constants.Files.JOB_STATE_DIR_NAME);
        try {
            if (Files.notExists(stateDir)) {
                Files.createDirectories(stateDir);
            }
        } catch (Throwable e) {
            log.error("Can't create attachments dir: {}", e.getMessage());
            return;
        }

        Path dst = stateDir.resolve(Constants.Files.LAST_ERROR_FILE_NAME);

        try (OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            SerializationUtils.serialize(out, t);
        } catch (Throwable e) {
            log.error("Can't save the last unhandled error: {}", e.getMessage());
        }

        try {
            Map<String, Object> error = ProcessErrorProcessor.process(t);

            Map<String, Object> outVars = OutVariablesParser.read(attachmentsDir);
            Map<String, Object> result = new HashMap<>(outVars);
            result.putAll(error);

            OutVariablesParser.write(attachmentsDir, result);
        } catch (Throwable e) {
            log.error("Can't write out variables: {}", e.getMessage());
        }
    }

    private static byte[] readIfExists(Path p) {
        try {
            if (Files.notExists(p)) {
                return null;
            }
            return Files.readAllBytes(p);
        } catch (Exception e) {
            log.warn("readIfExists ['{}'] -> error: {}", p, e.getMessage());
            return null;
        }
    }

    private static class SubClassesOf extends AbstractMatcher<TypeLiteral<?>> {

        private final Class<?> baseClass;

        private SubClassesOf(Class<?> baseClass) {
            this.baseClass = baseClass;
        }

        @Override
        public boolean matches(TypeLiteral<?> t) {
            return baseClass.isAssignableFrom(t.getRawType());
        }
    }

    private static class TaskClassesListener implements TypeListener {

        private final TaskClasses taskClasses;
        private final boolean debug;

        private TaskClassesListener(TaskClasses taskClasses, boolean debug) {
            this.taskClasses = taskClasses;
            this.debug = debug;
        }

        @SuppressWarnings("unchecked")
        public <T> void hear(TypeLiteral<T> typeLiteral, TypeEncounter<T> typeEncounter) {
            Class<Task> clazz = (Class<Task>) typeLiteral.getRawType();

            Named n = clazz.getAnnotation(Named.class);
            if (n == null) {
                return;
            }

            if (debug) {
                log.info("Registering {} as {}...", clazz, n);
            }

            taskClasses.add(n.value(), clazz);
        }
    }
}
