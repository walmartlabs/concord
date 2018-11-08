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

import com.walmartlabs.concord.common.PrivilegedAction;
import com.walmartlabs.concord.common.TruncBufferedReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.plugins.ansible.ArgUtils.*;

public class AnsibleRoles {

    public static void process(Path workDir, Path tmpDir, Map<String, Object> defaults,
                               Map<String,Object> args, AnsibleConfig cfg, boolean debug) throws Exception {
        new AnsibleRoles(workDir, tmpDir, defaults, debug).parse(args).enrich(cfg).downloadRoles();
    }

    private static final Logger log = LoggerFactory.getLogger(AnsibleRoles.class);

    private static final int SUCCESS_EXIT_CODE = 0;
    private static final String ROLE_DIR = "_roles";

    private final Path workDir;
    private final Path tmpDir;
    private final Map<String, Object> defaults;
    private final boolean debug;
    private final List<String> sensitiveData;

    private List<Map<String, String>> roles = Collections.emptyList();

    private AnsibleRoles(Path workDir, Path tmpDir, Map<String, Object> defaults, boolean debug) {
        this.workDir = workDir;
        this.tmpDir = tmpDir;
        this.defaults = defaults;
        this.debug = debug;

        this.sensitiveData = Collections.singletonList(getDefaultSrc());
    }

    private AnsibleRoles parse(Map<String, Object> args) {
        List<Map<String, Object>> in = getList(args, TaskParams.ROLES_KEY);
        if (in == null || in.isEmpty()) {
            return this;
        }

        roles = in.stream()
                .map(this::assertRole)
                .collect(Collectors.toList());

        return this;
    }

    private void downloadRoles() throws Exception {
        Path roleDir = workDir.relativize(tmpDir.resolve(ROLE_DIR));
        for(Map<String, String> e : roles) {
            Path dest = roleDir.resolve(e.get("name"));

            String[] cmd = new String[] {"git", "clone", e.get("src"), dest.toString()};
            executeCommand(workDir, cmd);

            String version = e.get("version");
            if (version != null) {
                executeCommand(dest, new String[] {"git", "checkout", version});
            }
        }
    }

    private void executeCommand(Path workDir, String[] cmd) throws Exception {
        ProcessBuilder b = new ProcessBuilder()
                .command(cmd)
                .directory(workDir.toFile())
                .redirectErrorStream(true);

        if (debug) {
            log.info("execute -> cmd: {}", String.join(" ", cmd));
        }

        Process p = PrivilegedAction.perform("task", b::start);

        try (BufferedReader reader = new TruncBufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("GIT: {}", hideSensitiveData(line));
            }
        }

        int code = p.waitFor();
        if (code != SUCCESS_EXIT_CODE) {
            log.warn("GIT finished with a non-zero exit code: {}", code);
            throw new IllegalStateException("GIT finished with exit code " + code);
        }
    }

    private AnsibleRoles enrich(AnsibleConfig config) {
        if (roles.isEmpty()) {
            return this;
        }

        ConfigSection cfg = config.getDefaults();
        cfg.prependPath("roles_path", ROLE_DIR);
        roles.forEach(r -> cfg.prependPath("roles_path", Paths.get(ROLE_DIR, r.get("name")).toString()));
        return this;
    }

    private String getDefaultSrc() {
        String src =  assertString("'roleSrc' is required in default 'ansibleParams'", defaults, "roleSrc");
        if (!src.endsWith("/")) {
            src += "/";
        }
        return src;
    }

    private Map<String, String> assertRole(Map<String,Object> r) {
        String name = assertString("Role 'name' is required", r, "name");

        String src = getString(r, "src");
        if (src == null || src.isEmpty()) {
            src = getDefaultSrc() + name;
            name = normalizeName(name);
        }

        String version = getString(r, "version");

        Map<String, String> result = new HashMap<>();
        result.put("name", name);
        result.put("src", src);
        if (version != null && !version.isEmpty()) {
            result.put("version", version);
        }

        return result;
    }

    private String hideSensitiveData(String s) {
        if (s == null) {
            return null;
        }

        for (String p : sensitiveData) {
            s = s.replaceAll(p, "***");
        }
        return s;
    }

    private static String normalizeName(String name) {
        int pos = name.indexOf('/');
        if (pos < 0) {
            return name;
        }
        return name.substring(pos + 1);
    }
}
