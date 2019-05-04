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
import java.util.*;
import java.util.stream.Collectors;

public class AnsibleInventory {

    public static void process(TaskContext ctx, PlaybookArgsBuilder playbook) throws IOException {
        List<String> inventories = new AnsibleInventory(ctx.getWorkDir(), ctx.getTmpDir(), ctx.isDebug())
                .write(ctx.getArgs());

        playbook.withInventories(inventories);
    }

    private static final Logger log = LoggerFactory.getLogger(AnsibleInventory.class);

    private final Path workDir;
    private final Path tmpDir;
    private final boolean debug;

    public AnsibleInventory(Path workDir, Path tmpDir, boolean debug) {
        this.workDir = workDir;
        this.tmpDir = tmpDir;
        this.debug = debug;
    }

    public List<String> write(Map<String, Object> args) throws IOException {
        List<Path> paths = writeInventory(args);

        if (debug) {
            for (Path p : paths) {
                log.info("INVENTORY: {}\n{}", p, new String(Files.readAllBytes(p)));
            }
        }

        return paths.stream()
                .map(workDir::relativize)
                .map(Path::toString)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Path> writeInventory(Map<String, Object> args) throws IOException {
        // try an "inline" inventory
        Object v = args.get(TaskParams.INVENTORY_KEY.getKey());

        // check if there are multiple entries
        if (v instanceof Collection) {
            List<Path> l = new ArrayList<>();
            for (Object vv : (Collection) v) {
                if (vv instanceof Map) {
                    l.add(processInventoryObject((Map<String, Object>) vv));
                } else {
                    throw new IllegalArgumentException("Invalid '" + TaskParams.INVENTORY_KEY + "' entry. Expected a map (YAML/JSON object), got: (" + vv.getClass() + ") " + vv);
                }
            }
            return l;
        } else if (v instanceof Map) {
            Path p = processInventoryObject((Map<String, Object>) v);
            return Collections.singletonList(p);
        } else if (v != null) {
            throw new IllegalArgumentException("Unsupported '" + TaskParams.INVENTORY_KEY + "' value. Expected an inventory object or a list of inventory objects, got: (" + v.getClass() + ") " + v);
        }

        // try a static inventory file
        v = args.get(TaskParams.INVENTORY_FILE_KEY.getKey());
        if (v instanceof Collection) {
            List<Path> l = new ArrayList<>();
            for (Object vv : (Collection) v) {
                if (vv instanceof String) {
                    l.add(processInventoryFile((String) vv));
                } else {
                    throw new IllegalArgumentException("Invalid '" + TaskParams.INVENTORY_FILE_KEY + "' entry. Expected a path to a file, got: (" + vv.getClass() + ") " + vv);
                }
            }
            return l;
        } else if (v instanceof String) {
            Path p = workDir.resolve(v.toString());
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                throw new IllegalArgumentException("File not found: " + v);
            }

            return Collections.singletonList(p);
        } else if (v != null) {
            throw new IllegalArgumentException("Unsupported '" + TaskParams.INVENTORY_FILE_KEY + "' value. Expected a path to an inventory file or a list of paths, got: (" + v.getClass() + ") " + v);
        }

        // try an "old school" inventory file
        Path p = workDir.resolve(TaskParams.INVENTORY_FILE_NAME.getKey());
        if (Files.exists(p)) {
            log.warn("{} is deprecated: use '{}' parameter if you want to specify an existing inventory file",
                    TaskParams.INVENTORY_FILE_NAME, TaskParams.INVENTORY_FILE_KEY);
            return Collections.singletonList(p);
        }

        // try a dynamic inventory script
        v = args.get(TaskParams.DYNAMIC_INVENTORY_FILE_KEY.getKey());
        if (v != null) {
            p = workDir.resolve(v.toString());
            Utils.updateScriptPermissions(p);
            log.info("Using a dynamic inventory script: {}", p);
            return Collections.singletonList(p);
        }

        // try an "old school" dynamic inventory script
        p = workDir.resolve(TaskParams.DYNAMIC_INVENTORY_FILE_NAME.getKey());
        if (Files.exists(p)) {
            Utils.updateScriptPermissions(p);
            log.info("Using a dynamic inventory script uploaded separately: {}", p);
            return Collections.singletonList(p);
        }

        // we can't continue without an inventory
        throw new IOException("Inventory is not defined");
    }

    private Path processInventoryObject(Map<String, Object> m) throws IOException {
        Path p = createInventoryFile(tmpDir, m);
        Utils.updateScriptPermissions(p);
        return p;
    }

    private Path processInventoryFile(String s) {
        Path p = workDir.resolve(s);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new IllegalArgumentException("Inventory file not found: " + s);
        }
        return p;
    }

    private Path createInventoryFile(Path tmpDir, Map<String, Object> m) throws IOException {
        Path p = Files.createTempFile(tmpDir, "inventory", ".sh");

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
