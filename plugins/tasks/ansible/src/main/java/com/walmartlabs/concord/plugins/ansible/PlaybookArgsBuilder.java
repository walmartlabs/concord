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

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class PlaybookArgsBuilder {

    private final String playbook;
    private final String inventory;
    private final Path workDir;
    private final Path tmpDir;

    private String attachmentsDir;
    private Map<String, Object> extraVars;
    private String user;
    private String tags;
    private String skipTags;
    private String privateKey;
    private String vaultPasswordFile;
    private Map<String, String> extraEnv = Collections.emptyMap();
    private String limit;
    private int verboseLevel = 0;

    public PlaybookArgsBuilder(String playbook, String inventory, Path workDir, Path tmpDir) {
        this.playbook = playbook;
        this.inventory = inventory;
        this.workDir = workDir;
        this.tmpDir = tmpDir;
    }

    public PlaybookArgsBuilder withExtraVars(Map<String, Object> extraVars) {
        this.extraVars = extraVars;
        return this;
    }

    public PlaybookArgsBuilder withUser(String user) {
        this.user = user;
        return this;
    }

    public PlaybookArgsBuilder withTags(String tags) {
        this.tags = tags;
        return this;
    }

    public PlaybookArgsBuilder withSkipTags(String skipTags) {
        this.skipTags = skipTags;
        return this;
    }

    public PlaybookArgsBuilder withPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public PlaybookArgsBuilder withAttachmentsDir(String attachmentsDir) {
        this.attachmentsDir = attachmentsDir;
        return this;
    }

    public PlaybookArgsBuilder withVaultPasswordFile(String vaultPasswordFile) {
        this.vaultPasswordFile = vaultPasswordFile;
        return this;
    }

    public PlaybookArgsBuilder withEnv(Map<String, String> env) {
        this.extraEnv = env;
        return this;
    }

    public PlaybookArgsBuilder withVerboseLevel(int level) {
        this.verboseLevel = level;
        return this;
    }

    public PlaybookArgsBuilder withLimit(String limit) {
        this.limit = limit;
        return this;
    }

    public List<String> buildArgs() throws IOException {
        List<String> l = new ArrayList<>(Arrays.asList("ansible-playbook", "-i", inventory, playbook));

        if (extraVars != null && !extraVars.isEmpty()) {
            l.add("--extra-vars");
            l.add("@" + workDir.relativize(writeExtraVars(extraVars, tmpDir)));
        }

        if (user != null) {
            l.add("-u");
            l.add(user);
        }

        if (tags != null) {
            l.add("-t");
            l.add(tags);
        }

        if (skipTags != null) {
            l.add("--skip-tags");
            l.add(skipTags);
        }

        if (privateKey != null) {
            l.add("--private-key");
            l.add(privateKey);
        }

        if (vaultPasswordFile != null) {
            l.add("--vault-password-file");
            l.add(vaultPasswordFile);
        }

        if (limit != null) {
            l.add("--limit");
            l.add(limit);
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
}
