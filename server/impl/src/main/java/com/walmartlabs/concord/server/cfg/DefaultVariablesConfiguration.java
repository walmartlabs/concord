package com.walmartlabs.concord.server.cfg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@Named
@Singleton
public class DefaultVariablesConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DefaultVariablesConfiguration.class);

    private static final String CFG_KEY = "DEF_VARS_CFG";

    private final Map<String, Object> vars;

    @SuppressWarnings("unchecked")
    public DefaultVariablesConfiguration() throws IOException {
        String path = System.getenv(CFG_KEY);
        if (path != null) {
            log.info("init -> using external default process variables configuration: {}", path);

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try (InputStream in = Files.newInputStream(Paths.get(path))) {
                this.vars = Optional.ofNullable(mapper.readValue(in, Map.class)).orElse(Collections.emptyMap());
            }
        } else {
            this.vars = Collections.emptyMap();

            log.warn("init -> no default process variables");
        }
    }

    public Map<String, Object> getVars() {
        return vars;
    }
}
