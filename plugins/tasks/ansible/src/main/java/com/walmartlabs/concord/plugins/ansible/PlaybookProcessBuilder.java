package com.walmartlabs.concord.plugins.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlaybookProcessBuilder {

    private static final Logger log = LoggerFactory.getLogger(PlaybookProcessBuilder.class);

    private final String playbook;
    private final String inventory;

    private String cfgFile;
    private Map<String, String> extraVars = Collections.emptyMap();
    private String user;
    private String tags;
    private String privateKey;

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

    public Process build() throws IOException {
        File pwd = new File(playbook);
        if (pwd.isFile()) {
            pwd = pwd.getParentFile();
            log.debug("build -> working directory: {}", pwd);
        }

        String[] cmd = formatCmd();
        log.debug("build -> cmd: {}", String.join(" ", cmd));

        ProcessBuilder b = new ProcessBuilder()
                .command(cmd)
                .directory(pwd)
                .redirectErrorStream(true);

        Map<String, String> env = b.environment();
        // TODO env.put("ANSIBLE_FORCE_COLOR", "true");
        if (cfgFile != null) {
            env.put("ANSIBLE_CONFIG", cfgFile);
        }

        return b.start();
    }

    private String[] formatCmd() throws IOException {
        List<String> l = new ArrayList<>(Arrays.asList(
                "ansible-playbook", "-T", "30", "-i", inventory, playbook,
                "-e", toJson(extraVars)));

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

        return l.toArray(new String[l.size()]);
    }

    private static String toJson(Map<String, String> m) throws IOException {
        return new ObjectMapper().writeValueAsString(m);
    }
}
