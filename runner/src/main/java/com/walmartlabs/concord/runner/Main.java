package com.walmartlabs.concord.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.*;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.Task;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import com.walmartlabs.concord.runner.engine.TaskClassHolder;
import io.takari.bpm.api.Engine;
import io.takari.bpm.api.Event;
import io.takari.bpm.api.EventService;
import io.takari.bpm.api.ExecutionException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Named
@Singleton
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final EngineFactory engineFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public Main(EngineFactory engineFactory) {
        this.engineFactory = engineFactory;
    }

    public void run() throws Exception {
        // determine current working directory, it should contain the payload
        Path baseDir = Paths.get(System.getProperty("user.dir"));
        log.info("run -> working directory: {}", baseDir.toAbsolutePath());

        // TODO constants
        Path idPath = baseDir.resolve(Constants.Files.INSTANCE_ID_FILE_NAME);
        while (!Files.exists(idPath)) {
            // TODO replace with WatchService
            Thread.sleep(100);
        }
        String instanceId = new String(Files.readAllBytes(idPath));

        String eventName = readResumeEvent(baseDir);
        if (eventName == null) {
            start(instanceId, baseDir);
        } else {
            resume(instanceId, baseDir, eventName);
        }
    }

    private void start(String instanceId, Path baseDir) throws ExecutionException {
        // read the request data
        Map<String, Object> req = readRequest(baseDir);

        // get active profiles from the request data
        Collection<String> activeProfiles = getActiveProfiles(req);

        // load the project
        ProjectDefinition project = loadProject(baseDir);

        // get the entry point
        String entryPoint = (String) req.get(Constants.Request.ENTRY_POINT_KEY);
        if (entryPoint == null) {
            throw new ExecutionException("Entry point must be set");
        }

        // prepare the process' arguments
        Map<String, Object> args = createArgs(instanceId, baseDir, req);

        // start the process
        log.debug("start ['{}', '{}'] -> entry point: {}, starting...", instanceId, baseDir, entryPoint);
        Engine e = engineFactory.create(project, baseDir, activeProfiles);
        e.start(instanceId, entryPoint, args);

        // save the suspended state marker if needed
        finalizeState(e, instanceId, baseDir);
    }

    private void resume(String instanceId, Path baseDir, String eventName) throws ExecutionException {
        // read the request data
        Map<String, Object> cfg = readRequest(baseDir);
        Map<String, Object> args = createArgs(instanceId, baseDir, cfg);

        log.debug("resume ['{}', '{}', '{}'] -> resuming...", instanceId, baseDir, eventName);

        // get active profiles from the request data
        Collection<String> activeProfiles = getActiveProfiles(cfg);

        // load the project
        ProjectDefinition project = loadProject(baseDir);

        // start the process
        Engine e = engineFactory.create(project, baseDir, activeProfiles);
        e.resume(instanceId, eventName, args);

        // save the suspended state marker if needed
        finalizeState(e, instanceId, baseDir);
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
            return new ProjectLoader().load(baseDir);
        } catch (IOException e) {
            throw new ExecutionException("Error while loading a project", e);
        }
    }

    private static String readResumeEvent(Path baseDir) throws IOException {
        Path p = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME)
                .resolve(Constants.Files.RESUME_MARKER_FILE_NAME);

        if (!Files.exists(p)) {
            return null;
        }

        return new String(Files.readAllBytes(p));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRequest(Path baseDir) throws ExecutionException {
        Path p = baseDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);

        try (InputStream in = Files.newInputStream(p)) {
            return objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ExecutionException("Error while reading request data", e);
        }
    }

    @SuppressWarnings("unchecked")
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
        m.put(Constants.Context.WORK_DIR_KEY, workDir.toAbsolutePath().toString());

        // initiator's info
        Object initiator = cfg.get(Constants.Request.INITIATOR_KEY);
        if (initiator != null) {
            m.put(Constants.Request.INITIATOR_KEY, initiator);
        }

        return m;
    }

    private static void finalizeState(Engine engine, String instanceId, Path baseDir) throws ExecutionException {
        Path stateDir = baseDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        try {
            Path resumeMarker = stateDir.resolve(Constants.Files.RESUME_MARKER_FILE_NAME);
            if (Files.exists(resumeMarker)) {
                Files.delete(resumeMarker);
            }

            Path suspendMarker = stateDir.resolve(Constants.Files.SUSPEND_MARKER_FILE_NAME);
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

                Path marker = stateDir.resolve(Constants.Files.SUSPEND_MARKER_FILE_NAME);
                Files.write(marker, eventNames);
                log.debug("finalizeState ['{}'] -> created the suspended marker", instanceId);
            }
        } catch (IOException e) {
            throw new ExecutionException("State saving error", e);
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            Injector injector = createInjector();
            Main main = injector.getInstance(Main.class);
            main.run();

            // force exit
            System.exit(0);
        } catch (Throwable e) {
            log.error("main -> unhandled exception", e);
            System.exit(1);
        }
    }

    private static Injector createInjector() {
        ClassLoader cl = Main.class.getClassLoader();
        Module m = new WireModule(new SpaceModule(new URLClassSpace(cl), BeanScanning.CACHE));

        // TODO: find a way to inject task classes directly
        Module tasks = new AbstractModule() {
            @Override
            protected void configure() {
                bindListener(new SubClassesOf(Task.class), new TaskClassesListener());
            }
        };

        return Guice.createInjector(tasks, m);
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
        @SuppressWarnings("unchecked")
        public <T> void hear(TypeLiteral<T> typeLiteral, TypeEncounter<T> typeEncounter) {
            Class<?> clazz = typeLiteral.getRawType();
            Named n = clazz.getAnnotation(Named.class);
            if (n != null) {
                TaskClassHolder h = TaskClassHolder.getInstance();
                h.register(n.value(), (Class<? extends Task>) clazz);
            }
        }
    }
}
