package com.walmartlabs.concord.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.format.AutoParser;
import com.walmartlabs.concord.common.format.DefinitionType;
import com.walmartlabs.concord.common.format.WorkflowDefinitionProvider;
import com.walmartlabs.concord.plugins.fs.FSDefinitionProvider;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import io.takari.bpm.ProcessDefinitionProvider;
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

    private final AutoParser parser;
    private final EngineFactory engineFactory;

    @Inject
    public Main(AutoParser parser, EngineFactory engineFactory) {
        this.parser = parser;
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
        // load process definitions
        WorkflowDefinitionProvider workflows = createWorkflowProvider(baseDir);

        // read the request data
        Map<String, Object> cfg = readRequest(baseDir);
        String entryPoint = (String) cfg.get(Constants.ENTRY_POINT_KEY);
        Map<String, Object> args = createArgs(instanceId, cfg);

        log.debug("start ['{}', '{}'] -> entry point: {}, starting...", instanceId, baseDir, entryPoint);

        // start the process
        Engine e = engineFactory.create(baseDir, workflows);
        e.start(instanceId, entryPoint, args);

        // save the suspended state marker if needed
        finalizeState(e, instanceId, baseDir);
    }

    private void resume(String instanceId, Path baseDir, String eventName) throws ExecutionException {
        // we don't need a real definitions provider, everything should be available from the state
        WorkflowDefinitionProvider workflows = createWorkflowProvider(baseDir);

        // read the request data
        Map<String, Object> cfg = readRequest(baseDir);
        Map<String, Object> args = createArgs(instanceId, cfg);

        log.debug("resume ['{}', '{}', '{}'] -> resuming...", instanceId, baseDir, eventName);

        // start the process
        Engine e = engineFactory.create(baseDir, workflows);
        e.resume(instanceId, eventName, args);

        // save the suspended state marker if needed
        finalizeState(e, instanceId, baseDir);
    }

    private WorkflowDefinitionProvider createWorkflowProvider(Path baseDir) throws ExecutionException {
        Map<String, String> attrs = Collections.singletonMap(
                Constants.LOCAL_PATH_ATTR, baseDir.toAbsolutePath().toString());

        Path scenariosDir = baseDir.resolve(Constants.DEFINITIONS_DIR_NAME);
        log.debug("createDefinitionProvider -> serving process definitions from '{}'", scenariosDir);

        return new FSDefinitionProvider(parser, attrs, scenariosDir,
                DefinitionType.CONCORD_YAML.getMask());
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
