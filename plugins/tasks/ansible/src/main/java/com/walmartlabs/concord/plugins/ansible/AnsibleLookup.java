package com.walmartlabs.concord.plugins.ansible;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class AnsibleLookup {

    public static void process(AnsibleContext context, AnsibleConfig cfg) {
        new AnsibleLookup(context.tmpDir())
                .enrich(cfg)
                .write();
    }

    private static final Logger log = LoggerFactory.getLogger(AnsibleLookup.class);

    private static final String LOOKUP_LOCATION = "/com/walmartlabs/concord/plugins/ansible/lookup";
    private static final String[] LOOKUPS = new String[]{
            "concord_data_secret.py",
            "concord_inventory.py",
            "concord_public_key_secret.py",
            "concord_secret.py" };

    private static final String LOOKUP_PLUGINS_DIR = "_lookups";

    private final Path tmpDir;

    public AnsibleLookup(Path tmpDir) {
        this.tmpDir = tmpDir;
    }

    public AnsibleLookup write() {
        try {
            Resources.copy(LOOKUP_LOCATION, LOOKUPS, getDir());
        } catch (IOException e) {
            log.error("Error while adding Concord lookup plugins: {}", e.getMessage(), e);
            throw new RuntimeException("Error while adding Concord lookup plugins: " + e.getMessage());
        }

        return this;
    }

    public AnsibleLookup enrich(AnsibleConfig config) {
        config.getDefaults()
                .prependPath("lookup_plugins", LOOKUP_PLUGINS_DIR);
        return this;
    }

    private Path getDir() {
        return tmpDir.resolve(LOOKUP_PLUGINS_DIR);
    }
}
