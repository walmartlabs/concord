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
import com.walmartlabs.concord.sdk.MapUtils;
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

    public static void process(AnsibleContext context, AnsibleConfig cfg, PlaybookScriptBuilder playbook) throws IOException {
        List<String> inventories = new AnsibleInventory(context.workDir(), context.tmpDir(), context.debug())
                .write(context.args(), cfg);

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

    public List<String> write(Map<String, Object> args, AnsibleConfig cfg) throws IOException {
        List<Path> paths = writeInventory(args, cfg);

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
    private List<Path> writeInventory(Map<String, Object> args, AnsibleConfig cfg) throws IOException {
        // try an "inline" inventory
        Object v = MapUtils.get(args, TaskParams.INVENTORY_KEY.getKey(), null);

        // check if there are multiple entries
        if (v instanceof Collection) {
            List<Path> l = new ArrayList<>();
            for (Object vv : (Collection<Object>) v) {
                if (vv instanceof String) {
                    l.add(processInventoryFile((String) vv));
                } else if (vv instanceof Map) {
                    l.add(processInventoryObject((Map<String, Object>) vv));
                } else {
                    throw new IllegalArgumentException("Invalid '" + TaskParams.INVENTORY_KEY.getKey() + "' entry. Expected a map (YAML/JSON object) or a path to a file, got: (" + vv.getClass() + ") " + vv);
                }
            }
            return l;
        } else if (v instanceof Map) {
            Path p = processInventoryObject((Map<String, Object>) v);
            return Collections.singletonList(p);
        } else if (v != null) {
            throw new IllegalArgumentException("Unsupported '" + TaskParams.INVENTORY_KEY.getKey() + "' value. Expected an inventory object or a list of inventory objects, got: (" + v.getClass() + ") " + v);
        }

        // try a static inventory file
        v = args.get(TaskParams.INVENTORY_FILE_KEY.getKey());
        if (v instanceof Collection) {
            List<Path> l = new ArrayList<>();
            for (Object vv : (Collection<Object>) v) {
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
            log.warn("Using a deprecated inventory source. Please use '{}', '{}' or '{}' parameter",
                    TaskParams.INVENTORY_KEY.getKey(), TaskParams.INVENTORY_FILE_KEY.getKey(),
                    TaskParams.DYNAMIC_INVENTORY_FILE_KEY.getKey());
            return Collections.singletonList(p);
        }

        // try the defaults from the configuration file
        v = cfg.getDefaults().getString("inventory");
        if (v != null) {
            p = workDir.resolve(v.toString());
            if (Files.exists(p)) {
                Utils.updateScriptPermissions(p);
                log.info("Using the inventory specified in the configuration file: {}", p);
                return Collections.singletonList(p);
            }
        }

        // we can't continue without an inventory
        throw new IOException("'" + TaskParams.INVENTORY_KEY.getKey() + "', '" + TaskParams.INVENTORY_FILE_KEY.getKey()
                + "' or '" + TaskParams.DYNAMIC_INVENTORY_FILE_KEY.getKey() + "' parameter is required");
    }

    private Path processInventoryObject(Map<String, Object> m) throws IOException {
        validateInventoryObject(m);

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

    @SuppressWarnings("unchecked")
    private static void validateInventoryObject(Map<String, Object> m) {
        if (m.isEmpty()) {
            throw new IllegalArgumentException("'" + TaskParams.INVENTORY_KEY.getKey() + "' object is empty. " +
                    "Check the task's input parameters.");
        }

        for (Map.Entry<String, Object> e : m.entrySet()) {
            String hostGroup = e.getKey();

            Object v = e.getValue();
            if (v == null) {
                throw new IllegalArgumentException("Invalid '" + TaskParams.INVENTORY_KEY.getKey() + "' value. " +
                        "The '" + hostGroup + "' host group is empty. Check the task's input parameters.");
            }

            if (!(v instanceof Map)) {
                throw new IllegalArgumentException("Invalid '" + TaskParams.INVENTORY_KEY.getKey() + "' value. " +
                        "The '" + hostGroup + "' host group must be a valid YAML/JSON object (Java Map instance), got: " + v + ". " +
                        "Check the task's input parameters.");
            }

            Map<String, Object> mm = (Map<String, Object>) v;
            Object vv = mm.get("hosts");
            if (vv == null) {
                continue;
            }

            if (!(vv instanceof Collection)) {
                throw new IllegalArgumentException("Invalid '" + TaskParams.INVENTORY_KEY.getKey() + "' value. " +
                        "The '" + hostGroup + ".hosts' parameter must be a list of strings (host names or IP addresses), got: " + vv + ". " +
                        "Check the task's input parameters.");
            }
        }
    }
}
