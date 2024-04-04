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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class PlaybookScriptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PlaybookScriptBuilder.class);

    private static final String ANSIBLE_CMD = "ansible-playbook";
    private static final String MISSING_ANSIBLE_CHECK =
            "command -v " + ANSIBLE_CMD + " >/dev/null 2>&1 || { echo \"" +
            "Can't find ansible-playbook binary in \\$PATH. Install a local copy or use " +
            "'" + TaskParams.DOCKER_IMAGE_KEY.getKey() + "' or '" + TaskParams.VIRTUALENV_KEY.getKey() + "' options." +
            "\" ; exit 1; }";

    private static final String VIRTUALENV_CMD = "virtualenv";
    private static final String MISSING_VIRTUALENV_CHECK =
            "command -v " + VIRTUALENV_CMD + " >/dev/null 2>&1 || { echo \"" +
            "Can't find virtualenv binary in \\$PATH. Install a local copy or use " +
            "'" + TaskParams.DOCKER_IMAGE_KEY.getKey() + "' option." +
            "\" ; exit 1; }";

    private final String playbook;
    private List<String> inventories;
    private final Path workDir;
    private final Path tmpDir;

    private boolean debug;
    private String attachmentsDir;
    private Map<String, Object> extraVars;
    private List<String> extraVarsFiles;
    private String extraSshArgs;
    private String user;
    private String tags;
    private String skipTags;
    private String privateKey;
    private Map<String, Path> vaultIds;
    private Map<String, String> extraEnv = Collections.emptyMap();
    private String limit;
    private boolean check;
    private boolean syntaxCheck;
    private boolean skipCheckBinary;
    private int verboseLevel = 0;
    private Virtualenv virtualenv;

    public PlaybookScriptBuilder(AnsibleContext context, String playbook) {
        this.playbook = playbook;
        this.workDir = context.workDir();
        this.tmpDir = context.tmpDir();
    }

    public PlaybookScriptBuilder withDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public PlaybookScriptBuilder withInventories(List<String> inventories) {
        this.inventories = inventories;
        return this;
    }

    public PlaybookScriptBuilder withExtraVars(Map<String, Object> extraVars) {
        this.extraVars = extraVars;
        return this;
    }

    public PlaybookScriptBuilder withExtraSshArgs(String extraSshArgs) {
        this.extraSshArgs = extraSshArgs;
        return this;
    }

    public PlaybookScriptBuilder withExtraVarsFiles(List<String> extraVarsFiles) {
        this.extraVarsFiles = extraVarsFiles;
        return this;
    }

    public PlaybookScriptBuilder withUser(String user) {
        this.user = user;
        return this;
    }

    public PlaybookScriptBuilder withTags(String tags) {
        this.tags = tags;
        return this;
    }

    public PlaybookScriptBuilder withSkipTags(String skipTags) {
        this.skipTags = skipTags;
        return this;
    }

    public PlaybookScriptBuilder withPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public PlaybookScriptBuilder withAttachmentsDir(String attachmentsDir) {
        this.attachmentsDir = attachmentsDir;
        return this;
    }

    public PlaybookScriptBuilder withVaultIds(Map<String, Path> vaultIds) {
        this.vaultIds = vaultIds;
        return this;
    }

    public PlaybookScriptBuilder withEnv(Map<String, String> env) {
        this.extraEnv = env;
        return this;
    }

    public PlaybookScriptBuilder withVerboseLevel(int level) {
        this.verboseLevel = level;
        return this;
    }

    public PlaybookScriptBuilder withLimit(String limit) {
        this.limit = limit;
        return this;
    }

    public PlaybookScriptBuilder withCheck(boolean check) {
        this.check = check;
        return this;
    }

    public PlaybookScriptBuilder withSyntaxCheck(boolean syntaxCheck) {
        this.syntaxCheck = syntaxCheck;
        return this;
    }

    public PlaybookScriptBuilder withVirtualenv(Virtualenv virtualenv) {
        this.virtualenv = virtualenv;
        return this;
    }

    public PlaybookScriptBuilder withSkipCheckBinary(boolean skipCheckBinary) {
        this.skipCheckBinary = skipCheckBinary;
        return this;
    }

    private List<String> buildAnsibleArgs() throws IOException {
        List<String> l = new ArrayList<>();
        l.add(ANSIBLE_CMD);

        for (String i : inventories) {
            l.add("-i");
            l.add(quote(i));
        }
        l.add(quote(playbook));

        if (extraVars != null && !extraVars.isEmpty()) {
            l.add("--extra-vars");
            l.add("@" + workDir.relativize(writeExtraVars(extraVars, tmpDir)));
        }

        if (extraVarsFiles != null) {
            extraVarsFiles.forEach(extraVarsFile -> {
                l.add("--extra-vars");
                l.add("@" + extraVarsFile);
            });
        }

        if (user != null) {
            l.add("-u");
            l.add(quote(user));
        }

        if (tags != null) {
            l.add("-t");
            l.add(quote(tags));
        }

        if (skipTags != null) {
            l.add("--skip-tags");
            l.add(quote(skipTags));
        }

        if (privateKey != null) {
            l.add("--private-key");
            l.add(quote(privateKey));
        }

        if (vaultIds != null) {
            for (Map.Entry<String, Path> p : vaultIds.entrySet()) {
                l.add("--vault-id");
                l.add(quote(p.getKey() + "@" + p.getValue().toString()));
            }
        }

        if (limit != null) {
            l.add("--limit");
            l.add(quote(limit));
        }

        if (check) {
            l.add("--check");
        }

        if (syntaxCheck) {
            l.add("--syntax-check");
        }

        if (extraSshArgs != null) {
            l.add("--ssh-extra-args=" + quote(extraSshArgs));
        }

        if (verboseLevel > 0) {
            if (verboseLevel > 4) {
                verboseLevel = 4;
            }

            StringBuilder b = new StringBuilder();
            for (int i = 0; i < verboseLevel; i++) {
                b.append("v");
            }

            l.add("-" + b);
        }

        return l;
    }

    public List<String> buildArgs() throws IOException {
        StringBuilder sb = new StringBuilder("#!/bin/bash").append("\n")
                .append("set -e").append("\n");

        if (virtualenv.isEnabled()) {
            if (!skipCheckBinary) {
                sb.append(MISSING_VIRTUALENV_CHECK).append("\n");
            }

            String virtualenvDir = workDir.relativize(virtualenv.getTargetDir()).toString();

            sb.append("virtualenv --no-download --system-site-packages ")
                    .append(virtualenvDir)
                    .append("\n");

            sb.append("source ").append(virtualenvDir).append("/bin/activate")
                    .append("\n");

            String indexUrl = virtualenv.getIndexUrl();

            List<String> packages = virtualenv.getPackages();
            if (packages != null && !packages.isEmpty()) {
                sb.append("pip install ");
                if (indexUrl != null) {
                    sb.append("-i").append(" '").append(indexUrl).append("' ");
                }

                packages.forEach(p -> sb.append("'").append(p).append("' "));

                sb.append("\n");
            }
        }

        if (!skipCheckBinary) {
            sb.append(MISSING_ANSIBLE_CHECK).append("\n");
        }

        if (debug) {
            sb.append(ANSIBLE_CMD).append(" --version").append("\n");
        }

        buildAnsibleArgs().forEach(arg -> sb.append(arg).append(" "));
        sb.append("\n");

        if (virtualenv.isEnabled()) {
            sb.append("deactivate").append("\n");
        }

        if (debug) {
            log.info("Using the run script:\n{}", sb);
        }

        Path dst = tmpDir.resolve("run.sh");
        Files.write(dst, sb.toString().getBytes(), StandardOpenOption.CREATE_NEW);
        return Arrays.asList("/bin/bash", workDir.relativize(dst).toString());
    }

    public Map<String, String> buildEnv() {
        Map<String, String> env = new HashMap<>();
        if (attachmentsDir != null) {
            env.put("_CONCORD_ATTACHMENTS_DIR", attachmentsDir);
        }
        env.putAll(extraEnv);
        return env;
    }

    private static Path writeExtraVars(Map<String, Object> extraVars, Path tmpDir) throws IOException {
        Path result = tmpDir.resolve("ansible-extra-vars.json");
        new ObjectMapper().writeValue(result.toFile(), extraVars);
        return result;
    }

    /**
     * Puts the specified string into single quotes.
     */
    private static String quote(String s) {
        return "'" + escapeQuote(s) + "'";
    }

    private static String escapeQuote(String s) {
        return s.replace("'", "'\\''");
    }
}
