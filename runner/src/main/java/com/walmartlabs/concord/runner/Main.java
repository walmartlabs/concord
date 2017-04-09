package com.walmartlabs.concord.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.ProjectDirectoryLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.ProjectDefinitionUtils;
import com.walmartlabs.concord.runner.engine.EngineFactory;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Named
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final EngineFactory engineFactory;

    @Inject
    public Main(EngineFactory engineFactory) {
        this.engineFactory = engineFactory;
    }

    public void run() throws Exception {
        String instanceId = System.getProperty("instanceId");
        if (instanceId == null) {
            throw new IllegalArgumentException("Instance ID must be specified");
        }

        // determine current working directory, it should contain the payload
        Path baseDir = Paths.get(System.getProperty("user.dir"));

        String eventName = readResumeEvent(baseDir);
        if (eventName == null) {
            start(instanceId, baseDir);
        } else {
            resume(instanceId, baseDir, eventName);
        }
    }

    private void start(String instanceId, Path baseDir) throws ExecutionException {
        // read the request data
        Map<String, Object> cfg = readRequest(baseDir);

        // get active profiles from the request data
        Collection<String> activeProfiles = getActiveProfiles(cfg);

        // load the project
        ProjectDefinition project = loadProject(baseDir);

        // get the project's variables
        Map<String, Object> projectVars = ProjectDefinitionUtils.getVariables(project, activeProfiles);

        // merge the project's variables with the request data
        cfg = ConfigurationUtils.deepMerge(projectVars, cfg);

        // get the entry point
        String entryPoint = (String) cfg.get(Constants.ENTRY_POINT_KEY);

        // prepare the process' arguments
        Map<String, Object> args = createArgs(instanceId, cfg);

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
        Map<String, Object> args = createArgs(instanceId, cfg);

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

    private static Collection<String> getActiveProfiles(Map<String, Object> cfg) {
        if (cfg == null) {
            return Collections.emptyList();
        }

        Object v = cfg.get(Constants.ACTIVE_PROFILES_KEY);
        if (v == null) {
            return Collections.emptyList();
        }

        return (Collection<String>) v;
    }

    private static ProjectDefinition loadProject(Path baseDir) throws ExecutionException {
        try {
            return new ProjectDirectoryLoader().load(baseDir);
        } catch (IOException e) {
            throw new ExecutionException("Error while loading a project", e);
        }
    }

    private static String readResumeEvent(Path baseDir) throws IOException {
        Path p = baseDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.JOB_STATE_DIR_NAME)
                .resolve(Constants.RESUME_MARKER_FILE_NAME);

        if (!Files.exists(p)) {
            return null;
        }

        return new String(Files.readAllBytes(p));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readRequest(Path baseDir) throws ExecutionException {
        Path p = baseDir.resolve(Constants.REQUEST_DATA_FILE_NAME);

        try (InputStream in = Files.newInputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            return om.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ExecutionException("Error while reading request data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createArgs(String instanceId, Map<String, Object> cfg) {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> args = (Map<String, Object>) cfg.get(Constants.ARGUMENTS_KEY);
        if (args != null) {
            m.putAll(args);
        }

        m.put(Constants.TX_ID_KEY, instanceId);
        return m;
    }

    private static void finalizeState(Engine engine, String instanceId, Path baseDir) throws ExecutionException {
        Path stateDir = baseDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.JOB_STATE_DIR_NAME);

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

                Path marker = stateDir.resolve(Constants.SUSPEND_MARKER_FILE_NAME);
                Files.write(marker, eventNames);
                log.debug("finalizeState ['{}'] -> created the suspended marker", instanceId);
            }
        } catch (IOException e) {
            throw new ExecutionException("State saving error", e);
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            ClassLoader cl = Main.class.getClassLoader();
            Module m = new WireModule(new SpaceModule(new URLClassSpace(cl), BeanScanning.CACHE));
            Injector injector = Guice.createInjector(m);

            Main main = injector.getInstance(Main.class);
            main.run();

            // force exit
            System.exit(0);
        } catch (Throwable e) {
            log.error("main -> unhandled exception", e);
            System.exit(1);
        }
    }
}
