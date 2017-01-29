package com.walmartlabs.concord.server.cfg;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

@Named
@Singleton
public class ServerConfigurationProvider implements Provider<ServerConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(ServerConfigurationProvider.class);

    public static final String SERVER_PORT_KEY = "SERVER_PORT";
    public static final String SERVER_EXPOSED_ADDRESS_KEY = "SERVER_EXPOSED_ADDRESS";
    public static final String SERVER_WORKDIR_KEY = "SERVER_WORKDIR";
    public static final String AGENT_IMAGE_NAME = "AGENT_IMAGE_NAME";

    @Override
    public ServerConfiguration get() {
        try {
            String exposedAddress = Utils.getEnv(SERVER_EXPOSED_ADDRESS_KEY, "http://127.0.0.1:8001");
            log.info("get -> using '{}' as an API endpoint address", exposedAddress);

            int port = Integer.parseInt(Utils.getEnv(SERVER_PORT_KEY, "8001"));
            String agentImageName = Utils.getEnv(AGENT_IMAGE_NAME, "walmartlabs/concord-agent");

            String s = Utils.getEnv(SERVER_WORKDIR_KEY, null);
            Path workDir;
            if (s == null) {
                workDir = Files.createTempDirectory("concord").toAbsolutePath();
            } else {
                workDir = new File(s).toPath();
            }

            return new ServerConfiguration(port, new URI(exposedAddress), agentImageName, workDir);
        } catch (IOException | URISyntaxException e) {
            Throwables.propagate(e);
        }
        return null;
    }
}
