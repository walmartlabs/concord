package com.walmartlabs.concord.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.format.AutoParser;
import com.walmartlabs.concord.common.format.DefinitionType;
import com.walmartlabs.concord.plugins.fs.FSDefinitionProvider;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.Engine;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Named
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String ARGUMENTS_KEY = "arguments";

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

        // load process definitions
        ProcessDefinitionProvider definitions = createDefinitionProvider(baseDir);

        // read the metadata
        Map<String, Object> cfg = readCfg(baseDir);
        String entryPoint = (String) cfg.get(Constants.ENTRY_POINT_KEY);
        Map<String, Object> args = createArgs(instanceId, cfg);

        // start the process
        Engine e = engineFactory.create(definitions);
        e.start(instanceId, entryPoint, args);

        // TODO handle suspend
    }

    private ProcessDefinitionProvider createDefinitionProvider(Path baseDir) throws ExecutionException {
        Map<String, String> attrs = Collections.singletonMap(
                Constants.LOCAL_PATH_ATTR, baseDir.toAbsolutePath().toString());

        Path scenariosDir = baseDir.resolve(Constants.DEFINITIONS_DIR_NAME);
        log.debug("createDefinitionProvider -> serving process definitions from '{}'", scenariosDir);

        return new FSDefinitionProvider(parser, attrs, scenariosDir,
                DefinitionType.CONCORD_YAML.getMask());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readCfg(Path baseDir) throws IOException {
        ObjectMapper om = new ObjectMapper();
        return om.readValue(baseDir.resolve(Constants.REQUEST_DATA_FILE_NAME).toFile(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createArgs(String instanceId, Map<String, Object> cfg) {
        Map<String, Object> m = new HashMap<>();

        Map<String, Object> args = (Map<String, Object>) cfg.get(ARGUMENTS_KEY);
        if (args != null) {
            m.putAll(args);
        }

        m.put(Constants.TX_ID_KEY, instanceId);
        return m;
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
