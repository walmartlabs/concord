package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.agent.api.AgentResource;
import com.walmartlabs.concord.server.cfg.AgentConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessAttachmentManager;
import com.walmartlabs.concord.server.process.ProcessException;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;

@Named
public class AttachmentsSavingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(AttachmentsSavingProcessor.class);

    private final ProcessAttachmentManager attachmentManager;
    private final AgentConfiguration agentCfg;

    @Inject
    public AttachmentsSavingProcessor(ProcessAttachmentManager attachmentManager, AgentConfiguration agentCfg) {
        this.attachmentManager = attachmentManager;
        this.agentCfg = agentCfg;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        String instanceId = payload.getInstanceId();
        try {
            saveAttachments(instanceId);
        } catch (IOException e) {
            throw new ProcessException("Error while saving attachments: " + instanceId, e);
        }
        return chain.process(payload);
    }

    private void saveAttachments(String instanceId) throws IOException {
        Client client = null;
        Response response = null;

        try {
            client = ClientBuilder.newClient();

            WebTarget t = client.target(agentCfg.getUri());
            AgentResource agentResource = ((ResteasyWebTarget) t).proxy(AgentResource.class);

            response = agentResource.downloadAttachments(instanceId);

            int status = response.getStatus();
            if (status != Status.OK.getStatusCode()) {
                if (status != Status.NOT_FOUND.getStatusCode()) {
                    throw new ProcessException("Agent response: " + status);
                }
                return;
            }

            try (InputStream in = response.readEntity(InputStream.class)) {
                attachmentManager.store(instanceId, in);
            }
        } finally {
            if (response != null) {
                response.close();
            }

            if (client != null) {
                client.close();
            }
        }
    }
}
