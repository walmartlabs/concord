package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.agent.api.AgentResource;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.cfg.AgentConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

@Named
public class StateTransferringProcessor implements PayloadProcessor {

    private final AgentConfiguration agentCfg;

    @Inject
    public StateTransferringProcessor(AgentConfiguration agentCfg) {
        this.agentCfg = agentCfg;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        Path dst = workspace.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        try {
            saveState(payload.getInstanceId(), dst);
        } catch (IOException e) {
            throw new ProcessException("Error while transferring a process state", e);
        }

        return chain.process(payload);
    }

    private void saveState(String instanceId, Path dst) throws IOException {
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

            Path stateDir = dst.resolve(Constants.Files.JOB_STATE_DIR_NAME);
            if (Files.exists(stateDir)) {
                IOUtils.deleteRecursively(stateDir);
            }

            try (ZipInputStream zip = new ZipInputStream(response.readEntity(InputStream.class))) {
                IOUtils.unzip(zip, dst);
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
