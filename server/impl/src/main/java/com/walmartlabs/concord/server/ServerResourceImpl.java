package com.walmartlabs.concord.server;

import com.walmartlabs.concord.server.api.PingResponse;
import com.walmartlabs.concord.server.api.ServerResource;
import org.sonatype.siesta.Resource;

import javax.inject.Named;

@Named
public class ServerResourceImpl implements ServerResource, Resource {

    public PingResponse ping() {
        return new PingResponse(true);
    }
}
