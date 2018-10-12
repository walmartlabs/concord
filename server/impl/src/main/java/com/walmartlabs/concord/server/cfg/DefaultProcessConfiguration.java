package com.walmartlabs.concord.server.cfg;

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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Default values for {@code configuration} object in processes.
 */
@Named
@Singleton
public class DefaultProcessConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessConfiguration.class);

    private final Map<String, Object> cfg;

    @Inject
    @SuppressWarnings("unchecked")
    public DefaultProcessConfiguration(@Config("process.defaultConfiguration") @Nullable String path) throws IOException {
        if (path == null) {
            log.warn("init -> no default process configuration");
            this.cfg = Collections.emptyMap();
            return;
        }

        log.info("init -> using external default process configuration: {}", path);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            this.cfg = Optional.ofNullable(mapper.readValue(in, Map.class)).orElse(Collections.emptyMap());
        }
    }

    @SuppressWarnings(value = "unchecked")
    public Map<String, Object> getCfg() {
        if (cfg.get("configuration") == null) {
            return Collections.emptyMap();
        }

        return (Map<String, Object>) cfg.get("configuration");
    }
}
