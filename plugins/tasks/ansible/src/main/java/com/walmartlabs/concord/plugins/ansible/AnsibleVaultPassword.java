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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class AnsibleVaultPassword {

    private static final Logger log = LoggerFactory.getLogger(AnsibleVaultPassword.class);

    private final Path workDir;
    private final Path tmpDir;

    private Path vaultPassword;

    public AnsibleVaultPassword(Path workDir, Path tmpDir) {
        this.workDir = workDir;
        this.tmpDir = tmpDir;
    }

    public static Path process(Path workDir, Path tmpDir, Map<String, Object> args) {
        return new AnsibleVaultPassword(workDir, tmpDir).load(args).getVaultPassword();
    }

    public AnsibleVaultPassword load(Map<String, Object> args) {
        try {
            vaultPassword = getVaultPasswordFilePath(args);
            return this;
        } catch (IOException e) {
            log.error("parse vault password error: {}", e.getMessage());
            throw new RuntimeException("parse vault password error: " + e.getMessage());
        }
    }

    public Path getVaultPassword() {
        if (vaultPassword == null) {
            return null;
        }
        return workDir.relativize(vaultPassword);
    }

    @SuppressWarnings("deprecation")
    private Path getVaultPasswordFilePath(Map<String, Object> args) throws IOException {
        // check if there is a path to a vault password file
        Path p = ArgUtils.getPath(args, TaskParams.VAULT_PASSWORD_FILE_KEY.getKey(), workDir);
        if (p != null) {
            if (isAScript(p)) {
                Utils.updateScriptPermissions(p);
            }

            log.info("Using the provided vault password file: {}", workDir.relativize(p));
            return p;
        }

        // try an "inline" password
        Object v = args.get(TaskParams.VAULT_PASSWORD_KEY.getKey());
        if (v instanceof String) {
            p = tmpDir.resolve("vault_password");
            Files.write(p, ((String) v).getBytes(), StandardOpenOption.CREATE);
            log.info("Using the provided vault password.");
            return p;
        } else if (v != null) {
            throw new IllegalArgumentException("Invalid '" + TaskParams.VAULT_PASSWORD_KEY.getKey() + "' type: " + v);
        }

        p = workDir.resolve(TaskParams.VAULT_PASSWORD_FILE_PATH.getKey());
        if (!Files.exists(p)) {
            return null;
        }

        return p;
    }

    private static boolean isAScript(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".sh") || n.endsWith(".py");
    }
}
