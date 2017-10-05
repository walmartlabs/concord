package com.walmartlabs.concord.plugins.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.DockerProcessBuilder;

import java.io.IOException;
import java.util.*;

public class DockerPlaybookProcessBuilder implements PlaybookProcessBuilder {

    private static final String VOLUME_CONTAINER_DEST = "/workspace";

    private String attachmentsDir;

    private final String txId;
    private final String imageName;
    private final String workdir;
    private final String playbook;
    private final String inventory;

    private String cfgFile;
    private Map<String, String> extraVars = Collections.emptyMap();
    private String user;
    private String tags;
    private String privateKey;
    private String vaultPasswordFile;
    private Map<String, String> extraEnv = Collections.emptyMap();
    private boolean debug = false;
    private boolean forcePull = true;
    private int verboseLevel = 0;

    public DockerPlaybookProcessBuilder(String txId, String imageName, String workdir, String playbook, String inventory) {
        this.txId = txId;
        this.imageName = imageName;
        this.workdir = workdir;
        this.playbook = relativize(workdir, playbook);
        this.inventory = relativize(workdir, inventory);
    }

    @Override
    public PlaybookProcessBuilder withCfgFile(String cfgFile) {
        this.cfgFile = relativize(workdir, cfgFile);
        return this;
    }

    @Override
    public PlaybookProcessBuilder withExtraVars(Map<String, String> extraVars) {
        this.extraVars = extraVars;
        return this;
    }

    @Override
    public PlaybookProcessBuilder withUser(String user) {
        this.user = user;
        return this;
    }

    @Override
    public PlaybookProcessBuilder withTags(String tags) {
        this.tags = tags;
        return this;
    }

    @Override
    public PlaybookProcessBuilder withPrivateKey(String privateKey) {
        this.privateKey = relativize(workdir, privateKey);
        return this;
    }

    @Override
    public PlaybookProcessBuilder withAttachmentsDir(String attachmentsDir) {
        this.attachmentsDir = relativize(workdir, attachmentsDir);
        return this;
    }

    @Override
    public PlaybookProcessBuilder withVaultPasswordFile(String vaultPasswordFile) {
        this.vaultPasswordFile = relativize(workdir, vaultPasswordFile);
        return this;
    }

    @Override
    public PlaybookProcessBuilder withEnv(Map<String, String> env) {
        this.extraEnv = env;
        return this;
    }

    @Override
    public PlaybookProcessBuilder withDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    @Override
    public PlaybookProcessBuilder withVerboseLevel(int level) {
        this.verboseLevel = level;
        return this;
    }

    public PlaybookProcessBuilder withForcePull(boolean forcePull) {
        this.forcePull = forcePull;
        return this;
    }

    @Override
    public Process build() throws IOException {
        return new DockerProcessBuilder(imageName)
                .addLabel(DockerProcessBuilder.CONCORD_TX_ID_LABEL, txId)
                .cleanup(true)
                .env(buildEnv())
                .volume(workdir, VOLUME_CONTAINER_DEST)
                .workdir(VOLUME_CONTAINER_DEST)
                .arg("ansible-playbook")
                .arg(playbook)
                .arg("-i", inventory)
                .args(buildAdditionalArgs())
                .debug(debug)
                .forcePull(forcePull)
                .build();
    }

    private Map<String, String> buildEnv() {
        Map<String, String> env = new HashMap<>();
        if (cfgFile != null) {
            env.put("ANSIBLE_CONFIG", cfgFile);
        }
        if (attachmentsDir != null) {
            env.put("_CONCORD_ATTACHMENTS_DIR", attachmentsDir);
        }
        env.putAll(extraEnv);
        return env;
    }

    private List<String> buildAdditionalArgs() throws IOException {
        List<String> result = new ArrayList<>();
        if (extraVars != null && !extraVars.isEmpty()) {
            result.add("-e");
            result.add(toJson(extraVars));
        }

        if (user != null) {
            result.add("-u");
            result.add(user);
        }

        if (tags != null) {
            result.add("-t");
            result.add(tags);
        }

        if (privateKey != null) {
            result.add("--private-key");
            result.add(privateKey);
        }

        if (vaultPasswordFile != null) {
            result.add("--vault-password-file");
            result.add(vaultPasswordFile);
        }

        if (verboseLevel > 0) {
            if (verboseLevel > 4) {
                verboseLevel = 4;
            }

            StringBuilder b = new StringBuilder();
            for (int i = 0; i < verboseLevel; i++) {
                b.append("v");
            }

            result.add("-" + b);
        }

        return result;
    }

    private static String relativize(String base, String path) {
        return path;
    }

    private static String toJson(Map<String, String> m) throws IOException {
        return new ObjectMapper().writeValueAsString(m);
    }
}
