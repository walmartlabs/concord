package com.walmartlabs.concord.plugins.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlaybookProcessBuilder {

    private static final Logger log = LoggerFactory.getLogger(PlaybookProcessBuilder.class);

    private String attachmentsDir;

    private final String playbook;
    private final String inventory;

    private String cfgFile;
    private Map<String, String> extraVars = Collections.emptyMap();
    private String user;
    private String tags;
    private String privateKey;
    private String vaultPasswordFile;

    public PlaybookProcessBuilder(String playbook, String inventory) {
        this.playbook = playbook;
        this.inventory = inventory;
    }

    public PlaybookProcessBuilder withCfgFile(String cfgFile) {
        this.cfgFile = cfgFile;
        return this;
    }

    public PlaybookProcessBuilder withExtraVars(Map<String, String> extraVars) {
        this.extraVars = extraVars;
        return this;
    }

    public PlaybookProcessBuilder withUser(String user) {
        this.user = user;
        return this;
    }

    public PlaybookProcessBuilder withTags(String tags) {
        this.tags = tags;
        return this;
    }

    public PlaybookProcessBuilder withPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public PlaybookProcessBuilder withAttachmentsDir(String attachmentsDir) {
        this.attachmentsDir = attachmentsDir;
        return this;
    }

    public PlaybookProcessBuilder withVaultPasswordFile(String vaultPasswordFile) {
        this.vaultPasswordFile = vaultPasswordFile;
        return this;
    }

    public Process build() throws IOException {
        File pwd = new File(playbook);
        if (pwd.isFile()) {
            pwd = pwd.getParentFile();
        }
        if (!pwd.exists()) {
            throw new IOException("Working directory not found: " + pwd);
        }
        log.info("build -> working directory: {}", pwd);

        String[] cmd = formatCmd();
        log.info("build -> cmd: {}", String.join(" ", cmd));

        ProcessBuilder b = new ProcessBuilder()
                .command(cmd)
                .directory(pwd)
                .redirectErrorStream(true);

        Map<String, String> env = b.environment();
        if (cfgFile != null) {
            env.put("ANSIBLE_CONFIG", cfgFile);
        }
        if (attachmentsDir != null) {
            env.put("_CONCORD_ATTACHMENTS_DIR", attachmentsDir);
        }
        log.info("build -> env: {}", env);

        return b.start();
    }

    private String[] formatCmd() throws IOException {
        List<String> l = new ArrayList<>(Arrays.asList("ansible-playbook", "-i", inventory, playbook));

        if (extraVars != null && !extraVars.isEmpty()) {
            l.add("-e");
            l.add(toJson(extraVars));
        }

        if (user != null) {
            l.add("-u");
            l.add(user);
        }

        if (tags != null) {
            l.add("-t");
            l.add(tags);
        }

        if (privateKey != null) {
            l.add("--private-key");
            l.add(privateKey);
        }

        if (vaultPasswordFile != null) {
            l.add("--vault-password-file");
            l.add(vaultPasswordFile);
        }

        return l.toArray(new String[l.size()]);
    }

    private static String toJson(Map<String, String> m) throws IOException {
        return new ObjectMapper().writeValueAsString(m);
    }
}
