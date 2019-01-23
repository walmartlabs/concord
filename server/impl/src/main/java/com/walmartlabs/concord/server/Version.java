package com.walmartlabs.concord.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class Version {

    private static final Version INSTANCE;
    static {
        Properties props = new Properties();

        try (InputStream in = ServerResource.class.getResourceAsStream("version.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String version = props.getProperty("version");
        String commitId = props.getProperty("commitId");
        String env = Utils.getEnv("CONCORD_ENV", "n/a");

        INSTANCE = new Version(version, commitId, env);
    }

    public static Version getCurrent() {
        return INSTANCE;
    }

    private final String version;
    private final String commitId;
    private final String env;

    public Version(String version, String commitId, String env) {
        this.version = version;
        this.commitId = commitId;
        this.env = env;
    }

    public String getVersion() {
        return version;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getEnv() {
        return env;
    }
}
