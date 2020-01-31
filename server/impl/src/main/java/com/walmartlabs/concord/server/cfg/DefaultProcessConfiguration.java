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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/**
 * Default values for {@code configuration} object in processes.
 */
@Deprecated
@Named
@Singleton
public class DefaultProcessConfiguration implements FileChangeNotifier.FileChangeListener {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessConfiguration.class);

    private static final TypeReference<Map<String, Object>> CFG_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final Object mutex = new Object();
    private Map<String, Object> cfg;

    @Inject
    public DefaultProcessConfiguration(@Config("process.defaultConfiguration") @Nullable String path) {
        if (path == null) {
            this.cfg = Collections.emptyMap();
            log.warn("init -> no default process configuration");
            return;
        }

        this.cfg = Collections.emptyMap();

        Map<String, Object> currentCfg = readCfg(Paths.get(path));
        if (currentCfg != null) {
            this.cfg = currentCfg;
        }

        FileChangeNotifier changeNotifier = new FileChangeNotifier(Paths.get(path), this);
        changeNotifier.start();

        log.info("init -> using external default process configuration: {}", path);
    }

    public Map<String, Object> getCfg() {
        synchronized (mutex) {
            return MapUtils.getMap(cfg, "configuration", Collections.emptyMap());
        }
    }

    @Override
    public void onChange(Path file) {
        Map<String, Object> newCfg = readCfg(file);
        if (newCfg == null) {
            return;
        }
        log.info("onChange ['{}'] -> default process configuration changed", file);
        synchronized (mutex) {
            this.cfg = newCfg;
        }
    }

    private static Map<String, Object> readCfg(Path path) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> cfg = mapper.readValue(in, CFG_TYPE);
            return cfg != null ? cfg : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("readCfg ['{}'] -> error while reading the configuration file: {}", path, e.getMessage());
            return null;
        }
    }
}
