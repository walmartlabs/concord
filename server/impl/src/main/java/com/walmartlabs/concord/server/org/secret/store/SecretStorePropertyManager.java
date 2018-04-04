package com.walmartlabs.concord.server.org.secret.store;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Class use to get the configurations for secret stores. It will look for <code>SECRET_STORE_CFG</code>
 * environment variable to read the central secret store properties file and store the configurations into the
 * {@link CaseInsensitiveProperties props} object.
 */
@Named
@Singleton
public class SecretStorePropertyManager {
    private static final Logger log = LoggerFactory.getLogger(SecretStorePropertyManager.class);
    private static final String SECRET_STORE_CFG = "SECRET_STORE_CFG";

    private final Properties props = new CaseInsensitiveProperties();

    public SecretStorePropertyManager() throws IOException {
        String path = System.getenv(SECRET_STORE_CFG);
        if (path != null) {
            log.info("init -> using the Secret store configuration file: {}", path);
            try (InputStream in = Files.newInputStream(Paths.get(path))) {
                props.load(in);
            }
        } else {
            log.warn("init -> no secret store configuration found, using default");
            try (InputStream in = ClassLoader.getSystemResourceAsStream("com/walmartlabs/concord/server/org/secret/secret_store.properties")) {
                props.load(in);
            }
        }
    }

    public String getProperty(String key) {
        return props.getProperty(key);
    }

    private class CaseInsensitiveProperties extends Properties {

        @Override
        public String getProperty(String key) {
            return super.getProperty(key.toLowerCase());
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            return super.getProperty(key.toLowerCase(), defaultValue);
        }

        @Override
        public synchronized Object put(Object key, Object value) {
            return super.put(((String) key).toLowerCase(), value);
        }
    }
}
