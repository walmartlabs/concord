package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import java.util.HashMap;
import java.util.Map;

public class AnsibleVaultId {

    public static void process(AnsibleContext context, PlaybookScriptBuilder playbook) {
        Map<String, Path> vaultIds = new AnsibleVaultId(context.workDir(), context.tmpDir())
                .parse(context.args())
                .getVaultIds();

        if (vaultIds != null) {
            playbook.withVaultIds(vaultIds);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AnsibleVaultId.class);

    private final Path workDir;
    private final Path tmpDir;

    private Map<String, Path> vaultIds;

    private AnsibleVaultId(Path workDir, Path tmpDir) {
        this.workDir = workDir;
        this.tmpDir = tmpDir;
    }

    private AnsibleVaultId parse(Map<String, Object> args) {
        try {
            vaultIds = getVaultIds(args);
            return this;
        } catch (IOException e) {
            log.error("Error while parsing the vault password configuration: {}", e.getMessage());
            throw new RuntimeException("Error while parsing the vault password configuration: " + e.getMessage());
        }
    }

    private Map<String, Path> getVaultIds() {
        if (vaultIds == null) {
            return null;
        }

        Map<String, Path> m = new HashMap<>();
        for (Map.Entry<String, Path> e : vaultIds.entrySet()) {
            if (e.getValue() != null) {
                m.put(e.getKey(), workDir.relativize(e.getValue()));
            }
        }

        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Path> getVaultIds(Map<String, Object> args) throws IOException {
        Map<String, Path> paths = new HashMap<>();

        // check if there is a path to a vault password file
        Object v = args.get(TaskParams.VAULT_PASSWORD_FILE_KEY.getKey());
        if (v instanceof String) {
            String key = TaskParams.VAULT_PASSWORD_FILE_KEY.getKey();
            paths.put(key, getVaultPasswordFilePath(key, v));
            return paths;

        } else if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                paths.put(e.getKey(), getVaultPasswordFilePath(e.getKey(), e.getValue()));
            }
            return paths;
        }

        // try an "inline" password
        v = args.get(TaskParams.VAULT_PASSWORD_KEY.getKey());
        if (v instanceof String) {
            String key = TaskParams.VAULT_PASSWORD_KEY.getKey();
            paths.put(key, storePassword(key, v));
            return paths;
        } else if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                paths.put(e.getKey(), storePassword(e.getKey(), e.getValue()));
            }
            return paths;
        } else if (v != null) {
            throw new IllegalArgumentException("Invalid '" + TaskParams.VAULT_PASSWORD_KEY.getKey() + "' type: " + v);
        }

        Path p = workDir.resolve(TaskParams.VAULT_PASSWORD_FILE_PATH.getKey());
        if (!Files.exists(p)) {
            return null;
        }

        return paths;
    }

    private Path getVaultPasswordFilePath(String key, Object o) throws IOException {
        Path p = ArgUtils.getPath(key, o, workDir);
        if (p != null) {
            if (isAScript(p)) {
                Utils.updateScriptPermissions(p);
            }

            log.info("Using the provided vault password file: {}", workDir.relativize(p));
        }
        return p;
    }

    private Path storePassword(String key, Object o) throws IOException {
        if (o == null) {
            throw new IllegalArgumentException("'" + key + "' vault's password is empty. " +
                    "Check the task's '" + TaskParams.VAULT_PASSWORD_KEY.getKey() + "' parameter.");
        }

        if (!(o instanceof String)) {
            throw new IllegalArgumentException("Invalid '" + key + "' vault's password value. " +
                    "Expected a string, got: " + o + ". " +
                    "Check the task's '" + TaskParams.VAULT_PASSWORD_KEY.getKey() + "' parameter.");
        }

        Path p = Files.createTempFile(tmpDir, key, ".vault");
        Files.write(p, ((String) o).getBytes(), StandardOpenOption.CREATE);
        return p;
    }

    private static boolean isAScript(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".sh") || n.endsWith(".py");
    }
}
