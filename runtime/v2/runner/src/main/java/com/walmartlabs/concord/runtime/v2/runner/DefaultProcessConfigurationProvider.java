package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.runner.guice.ObjectMapperProvider;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Waits for the process state files to appear in the  working directory and tries to
 * load the process' configuration from it.
 */
public class DefaultProcessConfigurationProvider implements Provider<ProcessConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessConfigurationProvider.class);

    private final Path workDir;
    private final ObjectMapper objectMapper;

    public DefaultProcessConfigurationProvider(Path workDir) {
        this.workDir = workDir;
        this.objectMapper = new ObjectMapperProvider().get();
    }

    @Override
    public ProcessConfiguration get() {
        try {
            UUID instanceId = waitForInstanceId(workDir);
            // TODO pass instanceId directly in _main.json?
            return readProcessConfiguration(instanceId, workDir);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Waits until an instanceId file appears in the specified directory
     * then reads it and parses as UUID.
     */
    @SuppressWarnings("BusyWait")
    private static UUID waitForInstanceId(Path workDir) {
        Path p = workDir.resolve(Constants.Files.INSTANCE_ID_FILE_NAME);
        while (true) {
            byte[] id = readIfExists(p);
            if (id != null && id.length > 0) {
                String s = new String(id);
                try {
                    return UUID.fromString(s.trim());
                } catch (IllegalArgumentException e) {
                    log.warn("waitForInstanceId ['{}'] -> value: '{}', error: {}", workDir, s, e.getMessage());
                }
            }

            // we are not using WatchService as it has issues when running in Docker
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ProcessConfiguration readProcessConfiguration(UUID instanceId, Path workDir) throws IOException {
        Path p = workDir.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        if (!Files.exists(p)) {
            return ProcessConfiguration.builder()
                    .instanceId(instanceId)
                    .build();
        }

        try (InputStream in = Files.newInputStream(p)) {
            return ProcessConfiguration.builder()
                    .from(objectMapper.readValue(in, ProcessConfiguration.class))
                    .instanceId(instanceId)
                    .build();
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
}
