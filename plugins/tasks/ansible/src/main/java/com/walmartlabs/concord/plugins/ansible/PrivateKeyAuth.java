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

import java.nio.file.Files;
import java.nio.file.Path;

public class PrivateKeyAuth implements AnsibleAuth {

    private static final Logger log = LoggerFactory.getLogger(PrivateKeyAuth.class);

    private final Path workDir;
    private final String username;
    private final Path keyPath;
    private final boolean removeAfter;

    public PrivateKeyAuth(Path workDir, String username, Path keyPath, boolean removeAfter) {
        this.workDir = workDir;
        this.username = username;
        this.keyPath = keyPath;
        this.removeAfter = removeAfter;
    }

    public Path getKeyPath() {
        return keyPath;
    }

    @Override
    public void prepare() {
        // do nothing
    }

    @Override
    public AnsibleAuth enrich(AnsibleEnv env, AnsibleContext context) {
        // do nothing
        return this;
    }

    @Override
    public AnsibleAuth enrich(PlaybookScriptBuilder p) {
        p.withUser(username)
                .withPrivateKey(workDir.relativize(keyPath).toString());
        return this;
    }

    @Override
    public void postProcess() {
        if (!removeAfter) {
            return;
        }

        try {
            Files.deleteIfExists(keyPath);
        } catch (Exception e) {
            log.warn("postProcess -> error", e);
        }
    }
}
