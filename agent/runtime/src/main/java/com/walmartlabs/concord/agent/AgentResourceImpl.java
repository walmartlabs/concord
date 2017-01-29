package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.agent.api.AgentResource;
import com.walmartlabs.concord.agent.api.PingResponse;
import org.sonatype.siesta.Resource;

import javax.inject.Named;

@Named
public class AgentResourceImpl implements AgentResource, Resource {

    @Override
    public PingResponse ping() {
        return new PingResponse(true);
    }
}
