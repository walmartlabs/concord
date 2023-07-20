package com.walmartlabs.concord.server.security.apikey.loader;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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
import com.walmartlabs.concord.server.cfg.ApiKeyConfiguration;
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ApiKeyLoader implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyLoader.class);

    private static final TypeReference<List<ApiKeyEntry>> LIST_API_KEY_ENTRIES = new TypeReference<List<ApiKeyEntry>>() {
    };

    private final Path loadFrom;
    private final ObjectMapper objectMapper;
    private final ApiKeyLoaderDao dao;

    @Inject
    public ApiKeyLoader(ApiKeyConfiguration cfg, ObjectMapper objectMapper, ApiKeyLoaderDao dao) {
        this.loadFrom = cfg.getLoadFrom();
        this.objectMapper = objectMapper;
        this.dao = dao;
    }

    @Override
    public void start() {
        if (this.loadFrom == null) {
            return;
        }

        log.info("Loading user API keys from {}...", this.loadFrom);
        try (InputStream in = Files.newInputStream(this.loadFrom)) {
            List<ApiKeyEntry> entries = objectMapper.readValue(in, LIST_API_KEY_ENTRIES);
            dao.upsert(entries);
        } catch (IOException e) {
            log.error("Error while loading the API keys file: {}", e.getMessage());
        }
    }
}
