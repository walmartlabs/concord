package com.walmartlabs.concord.server;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.server.api.PingResponse;
import com.walmartlabs.concord.server.api.ServerResource;
import com.walmartlabs.concord.server.api.VersionResponse;
import org.sonatype.siesta.Resource;

import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Named
public class ServerResourceImpl implements ServerResource, Resource {

    private final String version;

    public ServerResourceImpl() {
        Properties props = new Properties();

        try (InputStream in = ServerResourceImpl.class.getResourceAsStream("version.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        this.version = props.getProperty("version");
    }

    public PingResponse ping() {
        return new PingResponse(true);
    }

    @Override
    public VersionResponse version() {
        return new VersionResponse(version);
    }
}
