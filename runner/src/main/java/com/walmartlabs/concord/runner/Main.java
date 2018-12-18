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
import com.google.inject.*;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import com.walmartlabs.concord.runner.engine.ProcessErrorProcessor;
import com.walmartlabs.concord.sdk.Task;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Named
@Singleton
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String RESUME_MARKER = InternalConstants.Files.RESUME_MARKER_FILE_NAME;
    private static final String SUSPEND_MARKER = InternalConstants.Files.SUSPEND_MARKER_FILE_NAME;

    private final EngineFactory engineFactory;
    private final ProcessHeartbeat heartbeat;
    private final ApiClientFactory apiClientFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public Main(EngineFactory engineFactory, ProcessHeartbeat heartbeat, ApiClientFactory apiClientFactory) {
        this.engineFactory = engineFactory;
        this.heartbeat = heartbeat;
        this.apiClientFactory = apiClientFactory;
    }

    public void run() throws Exception {
        // determine current working directory, it should contain the payload
        Path baseDir = Paths.get(System.getProperty("user.dir"));
        log.info("run -> working directory: {}", baseDir.toAbsolutePath());

        Path idPath = baseDir.resolve(InternalConstants.Files.INSTANCE_ID_FILE_NAME);
        while (!Files.exists(idPath)) {
            // TODO replace with WatchService
            Thread.sleep(100);
        }

        UUID instanceId = UUID.fromString(new String(Files.readAllBytes(idPath)));

        Map<String, Object> policy = readPolicyRules(baseDir);
        if (policy.isEmpty()) {
            PolicyEngineHolder.INSTANCE.setEngine(null);
        } else {
            PolicyEngineHolder.INSTANCE.setEngine(new PolicyEngine(policy));
        }

        String sessionToken = getSessionToken(baseDir);
        heartbeat.start(instanceId, sessionToken);

        String agentId = ConfigurationUtils.getSystemProperty("agentId", "n/a");

        ProcessApiClient processApiClient = new ProcessApiClient(apiClientFactory.create(sessionToken));
        processApiClient.updateStatus(instanceId, agentId, ProcessEntry.StatusEnum.RUNNING);

        CheckpointManager checkpointManager = new CheckpointManager(instanceId, processApiClient);

        try {
            executeProcess(instanceId.toString(), checkpointManager, baseDir);
        } catch (Throwable e) {
            writeError(e, baseDir);
            throw e;
        }
    }

    private void writeError(Throwable e, Path baseDir) {
        Map<String, Object> error = ProcessErrorProcessor.process(e);

        Path storeDir = baseDir.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME);
        Map<String, Object> outVars = OutVariablesParser.read(storeDir);

        Map<String, Object> result = new HashMap<>(outVars);
        result.putAll(error);
        OutVariablesParser.write(storeDir, result);
    }

    private void executeProcess(String instanceId, CheckpointManager checkpointManager, Path baseDir) throws ExecutionException {
        // read the request data
        Map<String, Object> req = readRequest(baseDir);

        // get active profiles from the request data
        Collection<String> activeProfiles = getActiveProfiles(req);

        // load the project
        ProjectDefinition project = loadProject(baseDir);

        Set<String> metaVariables = getMetaVariables(req);

        Engine engine = engineFactory.create(project, baseDir, activeProfiles, metaVariables);

        Map<String, Object> resumeCheckpointReq = null;
        while (true) {
            Collection<Event> resultEvents;
            String eventName = readResumeEvent(baseDir);
            if (eventName == null) {
                resultEvents = start(engine, req, instanceId, baseDir);
            } else {
                resultEvents = resume(engine, req, instanceId, baseDir, eventName);
            }

            Event checkpointEvent = resultEvents.stream()
                    .filter(e -> e.getPayload() instanceof Map)
                    .filter(e -> getCheckpointId(e) != null)
                    .findFirst()
                    .orElse(null);

            if (checkpointEvent != null) {
                checkpointManager.process(getCheckpointId(checkpointEvent), checkpointEvent.getName(), baseDir);
                // clear arguments
                if (resumeCheckpointReq == null) {
                    resumeCheckpointReq = new HashMap<>(req);
                    resumeCheckpointReq.remove(InternalConstants.Request.ARGUMENTS_KEY);
                }
                req = resumeCheckpointReq;
            } else {
                return;
            }
        }
    }

    private static UUID getCheckpointId(Event e) {
        String s = (String) ((Map) e.getPayload()).get("checkpointId");
        if (s == null) {
            return null;
        }
        return UUID.fromString(s);
    }

    private Collection<Event> start(Engine e, Map<String, Object> req, String instanceId, Path baseDir) throws ExecutionException {
        // get the entry point
        String entryPoint = (String) req.get(InternalConstants.Request.ENTRY_POINT_KEY);
        if (entryPoint == null) {
            throw new ExecutionException("Entry point must be set");
        }

        // prepare the process' arguments
        Map<String, Object> args = createArgs(instanceId, baseDir, req);

        Object initiator = req.get(InternalConstants.Request.INITIATOR_KEY);
        if (initiator != null) {
            args.put(InternalConstants.Request.INITIATOR_KEY, initiator);
            args.put(InternalConstants.Request.CURRENT_USER_KEY, initiator);
        }

        // start the process
        log.debug("start ['{}', '{}'] -> entry point: {}, starting...", instanceId, baseDir, entryPoint);

        e.start(instanceId, entryPoint, args);

        // save the suspended state marker if needed
        return finalizeState(e, instanceId, baseDir);
    }

    private Collection<Event> resume(Engine e, Map<String, Object> req, String instanceId, Path baseDir, String eventName) throws ExecutionException {
        Map<String, Object> args = createArgs(instanceId, baseDir, req);

        Object currentUser = req.get(InternalConstants.Request.CURRENT_USER_KEY);
        if (currentUser != null) {
            args.put(InternalConstants.Request.CURRENT_USER_KEY, currentUser);
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

        Object v = cfg.get(InternalConstants.Request.ACTIVE_PROFILES_KEY);
        if (v == null) {
            return Collections.emptyList();
        }

        return (Collection<String>) v;
    }

    private static ProjectDefinition loadProject(Path baseDir) throws ExecutionException {
        try {
            return new ProjectLoader().loadProject(baseDir);
        } catch (IOException e) {
            throw new ExecutionException("Error while loading a project", e);
        }
    }

    private static String readResumeEvent(Path baseDir) throws ExecutionException {
        Path p = baseDir.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(InternalConstants.Files.JOB_STATE_DIR_NAME)
                .resolve(InternalConstants.Files.RESUME_MARKER_FILE_NAME);

        if (!Files.exists(p)) {
            return null;
        }

        try {
            return new String(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new ExecutionException("Read resume event error", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRequest(Path baseDir) throws ExecutionException {
        Path p = baseDir.resolve(InternalConstants.Files.REQUEST_DATA_FILE_NAME);

        try (InputStream in = Files.newInputStream(p)) {
            return objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ExecutionException("Error while reading request data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPolicyRules(Path ws) throws ExecutionException {
        Path policyFile = ws.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME).resolve(InternalConstants.Files.POLICY_FILE_NAME);
        if (!Files.exists(policyFile)) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(policyFile.toFile(), Map.class);
        } catch (IOException e) {
            throw new ExecutionException("Error while reading policy rules");
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private static Map<String, Object> createArgs(String instanceId, Path workDir, Map<String, Object> cfg) {
        Map<String, Object> m = new LinkedHashMap<>();

        // original arguments
        Map<String, Object> args = (Map<String, Object>) cfg.get(InternalConstants.Request.ARGUMENTS_KEY);
        if (args != null) {
            m.putAll(args);
        }

        // instance ID
        m.put(InternalConstants.Context.TX_ID_KEY, instanceId);

        // workDir
        String dir = workDir.toAbsolutePath().toString();
        m.put(InternalConstants.Context.LOCAL_PATH_KEY, dir);
        m.put(InternalConstants.Context.WORK_DIR_KEY, dir);

        // out variables
        Object outExpr = cfg.get(InternalConstants.Request.OUT_EXPRESSIONS_KEY);
        if (outExpr != null) {
            m.put(InternalConstants.Context.OUT_EXPRESSIONS_KEY, outExpr);
        }

        return m;
    }

    @SuppressWarnings("unchecked")
    private Set<String> getMetaVariables(Map<String, Object> cfg) {
        Map<String, Object> meta = (Map<String, Object>) cfg.get(InternalConstants.Request.META);
        if (meta != null) {
            return meta.keySet();
        }
        return Collections.emptySet();
    }

    private static Collection<Event> finalizeState(Engine engine, String instanceId, Path baseDir) throws ExecutionException {
        Path stateDir = baseDir.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(InternalConstants.Files.JOB_STATE_DIR_NAME);

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
        try {
            List<URL> deps = Collections.emptyList();
            if (args.length > 0) {
                deps = parseDeps(Paths.get(args[0]));
            }

            URLClassLoader depsClassLoader = new URLClassLoader(deps.toArray(new URL[0]), Main.class.getClassLoader()); // NOSONAR
            Thread.currentThread().setContextClassLoader(depsClassLoader);

            Injector injector = createInjector(depsClassLoader);
            Main main = injector.getInstance(Main.class);
            main.run();

            // force exit
            System.exit(0);
        } catch (Exception e) {
            // try to unroll nested exceptions to get a meaningful one
            Throwable t = unroll(e);
            log.error("main -> unhandled exception", t);
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static String getSessionToken(Path baseDir) throws ExecutionException {
        Path p = baseDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(InternalConstants.Files.SESSION_TOKEN_FILE_NAME);

        try {
            return new String(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new ExecutionException("Error while reading sesison token data", e);
        }
    }

    private static void installSecurityManager() {
        String s = System.getProperty("concord.securityManager.enable");
        if (!"true".equals(s)) {
            return;
        }

        System.setSecurityManager(new ConcordSecurityManager());
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

        return e;
    }

    private static List<URL> parseDeps(Path depsListFile) throws IOException {
        List<URL> result = Files.readAllLines(depsListFile).stream()
                .map(s -> {
                    try {
                        return new URL("file://" + s);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("invalid deps file: " + depsListFile + ", error:" + e.getMessage());
                    }
                }).collect(Collectors.toList());

        // payload's own libraries are stored in `./lib/` directory in the working directory
        Path baseDir = Paths.get(System.getProperty("user.dir"));
        Path lib = baseDir.resolve(InternalConstants.Files.LIBRARIES_DIR_NAME);
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

    private static Injector createInjector(ClassLoader depsClassLoader) {
        ClassLoader cl = Main.class.getClassLoader();

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
                tasks,
                taskCallModule,
                new SpaceModule(new URLClassSpace(cl), BeanScanning.CACHE),
                new SpaceModule(new URLClassSpace(depsClassLoader), BeanScanning.CACHE));

        return Guice.createInjector(m);
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
