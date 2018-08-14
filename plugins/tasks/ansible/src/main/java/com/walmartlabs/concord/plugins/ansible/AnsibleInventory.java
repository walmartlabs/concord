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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class AnsibleInventory {

    private static final Logger log = LoggerFactory.getLogger(AnsibleInventory.class);

    private final Path workDir;
    private final Path tmpDir;
    private final boolean debug;

    public AnsibleInventory(Path workDir, Path tmpDir, boolean debug) {
        this.workDir = workDir;
        this.tmpDir = tmpDir;
        this.debug = debug;
    }

    public Path write(Map<String, Object> args) throws IOException {
        Path i = writeInventory(args);
        if (debug) {
            log.info("INVENTORY: {}", new String(Files.readAllBytes(i)));
        }
        return workDir.relativize(i);
    }

    @SuppressWarnings("unchecked")
    private Path writeInventory(Map<String, Object> args) throws IOException {
        // try an "inline" inventory
        Object v = args.get(TaskParams.INVENTORY_KEY.getKey());
        if (v instanceof Map) {
            Path p = createInventoryFile(tmpDir, (Map<String, Object>) v);
            Utils.updateScriptPermissions(p);
            log.info("Using an inline inventory");
            return p;
        }

        // try a static inventory file
        v = args.get(TaskParams.INVENTORY_FILE_KEY.getKey());
        if (v != null) {
            Path p = workDir.resolve(v.toString());
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                throw new IllegalArgumentException("File not found: " + v);
            }
            log.info("Using a static inventory file: {}", p);
            return p;
        }

        // try an "old school" inventory file
        Path p = workDir.resolve(TaskParams.INVENTORY_FILE_NAME.getKey());
        if (Files.exists(p)) {
            log.info("Using a static inventory file uploaded separately: {}", p);
            return p;
        }

        // try a dynamic inventory script
        v = args.get(TaskParams.DYNAMIC_INVENTORY_FILE_KEY.getKey());
        if (v != null) {
            p = workDir.resolve(v.toString());
            Utils.updateScriptPermissions(p);
            log.info("Using a dynamic inventory script: {}", p);
            return p;
        }

        // try an "old school" dynamic inventory script
        p = workDir.resolve(TaskParams.DYNAMIC_INVENTORY_FILE_NAME.getKey());
        if (Files.exists(p)) {
            Utils.updateScriptPermissions(p);
            log.info("Using a dynamic inventory script uploaded separately: {}", p);
            return p;
        }

        // we can't continue without an inventory
        throw new IOException("Inventory is not defined");
    }

    private Path createInventoryFile(Path tmpDir, Map<String, Object> m) throws IOException {
        Path p = tmpDir.resolve("inventory.sh");

        try (BufferedWriter w = Files.newBufferedWriter(p, StandardOpenOption.CREATE)) {
            w.write("#!/bin/sh");
            w.newLine();
            w.write("cat << \"EOF\"");
            w.newLine();

            ObjectMapper om = new ObjectMapper();
            String s = om.writeValueAsString(m);
            w.write(s);
            w.newLine();

            w.write("EOF");
            w.newLine();
        }

        return p;
    }
}
