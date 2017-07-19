package com.walmartlabs.concord.plugins.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.DockerProcessBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class DockerPlaybookProcessBuilder {

    private static final Logger log = LoggerFactory.getLogger(DockerPlaybookProcessBuilder.class);

    private static final String VOLUME_CONTAINER_DEST = "/workspace";

    private String dockerImageName;

    private String attachmentsDir;

    private final String pwd;
    private final String playbook;
    private final String inventory;

    private String cfgFile;
    private Map<String, String> extraVars = Collections.emptyMap();
    private String user;
    private String tags;
    private String privateKey;
    private String vaultPasswordFile;

    public DockerPlaybookProcessBuilder(String pwd, String playbook, String inventory) {
        this.pwd = pwd;
        this.playbook = relativize(pwd, playbook);
        this.inventory = relativize(pwd, inventory);
    }

    public DockerPlaybookProcessBuilder withCfgFile(String cfgFile) {
        this.cfgFile = relativize(pwd, cfgFile);
        return this;
    }

    public DockerPlaybookProcessBuilder withExtraVars(Map<String, String> extraVars) {
        this.extraVars = extraVars;
        return this;
    }

    public DockerPlaybookProcessBuilder withUser(String user) {
        this.user = user;
        return this;
    }

    public DockerPlaybookProcessBuilder withTags(String tags) {
        this.tags = tags;
        return this;
    }

    public DockerPlaybookProcessBuilder withPrivateKey(String privateKey) {
        this.privateKey = relativize(pwd, privateKey);
        return this;
    }

    public DockerPlaybookProcessBuilder withAttachmentsDir(String attachmentsDir) {
        this.attachmentsDir = relativize(pwd, attachmentsDir);
        return this;
    }

    public DockerPlaybookProcessBuilder withVaultPasswordFile(String vaultPasswordFile) {
        this.vaultPasswordFile = relativize(pwd, vaultPasswordFile);
        return this;
    }

    public DockerPlaybookProcessBuilder dockerImageName(String dockerImageName) {
        this.dockerImageName = dockerImageName;
        return this;
    }

    public Process build() throws IOException {
        return new DockerProcessBuilder(dockerImageName)
                .cleanup(true)
                .env(buildEnv())
                .volume(pwd, VOLUME_CONTAINER_DEST)
                .arg("ansible-playbook")
                .arg(playbook)
                .arg("-i", inventory)
                .args(buildAdditionalArgs())
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
        log.info("buildAdditionalArgs '{}'", result);
        return result;
    }

    private static String relativize(String base, String path) {
        if(path == null) {
            return null;
        }
        return Paths.get(base).relativize(Paths.get(path)).toString();
    }

    private static String toJson(Map<String, String> m) throws IOException {
        return new ObjectMapper().writeValueAsString(m);
    }
}
