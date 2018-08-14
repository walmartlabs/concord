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

public class AnsibleStrategy {

    private static final Logger log = LoggerFactory.getLogger(AnsibleStrategy.class);

    private static final String STRATEGY_LOCATION = "/com/walmartlabs/concord/plugins/ansible/strategy";
    private static final String[] STRATEGIES = new String[]{"concord_free.py", "concord_linear.py"};

    private static final String STRATEGY_PLUGINS_DIR = "_strategy";

    private final Path tmpDir;

    public AnsibleStrategy(Path tmpDir) {
        this.tmpDir = tmpDir;
    }

    public static void process(Path tmpDir, AnsibleConfig cfg) {
        new AnsibleStrategy(tmpDir).enrich(cfg).write();
    }

    public AnsibleStrategy write() {
        try {
            Resources.copy(STRATEGY_LOCATION, STRATEGIES, getDir());
        } catch (IOException e) {
            log.error("write lookups error: {}", e.getMessage() );
            throw new RuntimeException("write lookups error: " + e.getMessage());
        }

        return this;
    }

    public AnsibleStrategy enrich(AnsibleConfig config) {
        config.getDefaults()
                .prependPath("strategy_plugins", STRATEGY_PLUGINS_DIR);
        return this;
    }

    private Path getDir() {
        return tmpDir.resolve(STRATEGY_PLUGINS_DIR);
    }
}
