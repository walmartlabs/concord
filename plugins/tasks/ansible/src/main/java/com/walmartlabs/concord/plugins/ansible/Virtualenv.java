package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Virtualenv {

    private static final Logger log = LoggerFactory.getLogger(Virtualenv.class);

    @SuppressWarnings("unchecked")
    public static Virtualenv create(AnsibleContext context) {
        Virtualenv virtualenv = new Virtualenv(context.tmpDir().resolve("ve")); // can't be "virtualenv", it breaks initial install

        Object v = context.args().get(TaskParams.VIRTUALENV_KEY.getKey());
        if (v == null) {
            return virtualenv;
        }

        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Invalid '" + TaskParams.VIRTUALENV_KEY.getKey() + "' value. " +
                    "Expected a virtualenv definition, got: " + v);
        }

        virtualenv.enabled = true;

        Map<String, Object> m = (Map<String, Object>) v;

        Object packages = m.get("packages");
        if (packages != null) {
            if (!(packages instanceof List)) {
                throw new IllegalArgumentException("Invalid virtualenv package list value. " +
                        "Expected a list of package names, got: " + packages);
            }

            List<Object> l = (List<Object>) packages;
            l.forEach(p -> addPackage(virtualenv, p));
        }

        virtualenv.indexUrl = Utils.assertArgSafe(getIndexUrl(context.defaults(), m));
        virtualenv.tool = getTool(m);

        return virtualenv;
    }

    public enum Tool {
        VENV("venv"),
        VIRTUALENV("virtualenv");

        private final String key;

        Tool(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static Tool fromString(String s) {
            for (Tool t : values()) {
                if (t.key.equalsIgnoreCase(s)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Invalid virtualenv 'tool' value: '" + s + "'. " +
                    "Expected one of: " + Arrays.stream(values()).map(Tool::getKey).collect(Collectors.toList()));
        }
    }

    private final Path targetDir;
    private final List<String> packages = new ArrayList<>();

    private boolean enabled = false;
    private String indexUrl;
    private Tool tool;

    private Virtualenv(Path targetDir) {
        this.targetDir = targetDir;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getPackages() {
        return packages;
    }

    public String getIndexUrl() {
        return indexUrl;
    }

    /**
     * @return the explicitly requested tool or {@code null} to use venv with a virtualenv fallback
     */
    public Tool getTool() {
        return tool;
    }

    public Path getTargetDir() {
        return targetDir;
    }

    public void destroy() {
        try {
            PathUtils.deleteRecursively(targetDir);
        } catch (IOException e) {
            log.warn("Error while removing the virtualenv's directory: {}", e.getMessage());
        }
    }

    private static void addPackage(Virtualenv v, Object p) {
        if (!(p instanceof String)) {
            throw new IllegalArgumentException("Invalid virtualenv package '" + p + "'");
        }

        String s = Utils.assertArgSafe((String) p);
        v.packages.add(s);
    }

    private static Tool getTool(Map<String, Object> m) {
        Object tool = m.get("tool");
        if (tool == null) {
            return null;
        }

        if (!(tool instanceof String)) {
            throw new IllegalArgumentException("Invalid virtualenv 'tool' value. " +
                    "Expected a string, got: " + tool);
        }

        return Tool.fromString((String) tool);
    }

    private static String getIndexUrl(Map<String, Object> defaults, Map<String, Object> m) {
        Object indexUrl = m.get("indexUrl");

        if (indexUrl != null) {
            if (!(indexUrl instanceof String)) {
                throw new IllegalArgumentException("Invalid virtualenv 'indexUrl' value. " +
                        "Expected a string, got: " + indexUrl);
            }

            return (String) indexUrl;
        }

        return MapUtils.getString(MapUtils.getMap(defaults, "virtualenv", Collections.emptyMap()), "indexUrl", null);
    }
}
