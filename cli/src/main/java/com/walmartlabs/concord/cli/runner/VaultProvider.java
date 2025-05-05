package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Implements a simple "vault" system.
 * When using {@code crypto.decryptString} in a flow executed by the CLI,
 * instead of decrypting the key, it performs a simple lookup.
 * E.g.
 * <pre>
 * {@code
 * # concord.yml
 * flows:
 *   default:
 *     - log: "${crypto.decryptString('abc')}" # prints out "the_actual_value"
 * }
 * </pre>
 * <pre>
 * {@code
 * # ~/.concord/vaults/default
 * abc = the_actual_value
 * }
 * </pre>
 */
// TODO consider implementing support for encrypted Ansible vaults
public class VaultProvider {

    private final String id;
    private final Map<String, String> items;

    public VaultProvider(Path dir, String id) {
        this.id = id;
        this.items = load(vaultPath(dir, id));
    }

    public String getValue(String key) {
        if (items == null) {
            System.err.println("Vault '" + id + "' not found. Can't get value for key '" + key + "'");
            throw new RuntimeException("Vault not configured");
        }

        if (!items.containsKey(key)) {
            System.out.println(ansi().fgRed().a("There are no key '").a(key).a("' in vault '").a(id).a("'").reset());
        }

        return items.get(key);
    }

    private static Map<String, String> load(Path file) {
        if (Files.notExists(file)) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        try (InputStream is = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(is);
            for (String name: props.stringPropertyNames()) {
                result.put(name, props.getProperty(name));
            }
            return result;
        } catch (IOException e) {
            System.out.println(ansi().fgBrightRed().a("Error loading vault file '").a(file).a("': ").a(e.getMessage()).reset());
            throw new RuntimeException(e.getMessage());
        }
    }

    private static Path vaultPath(Path dir, String vaultId) {
        return dir.resolve(vaultId);
    }
}
