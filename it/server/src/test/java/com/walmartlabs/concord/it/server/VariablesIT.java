package com.walmartlabs.concord.it.server;

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.EncryptValueRequest;
import com.walmartlabs.concord.server.api.project.EncryptValueResponse;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class VariablesIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        String projectName = "project_" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName, null, null,
                ImmutableMap.of("arguments",
                        ImmutableMap.of("nested",
                                ImmutableMap.of(
                                        "y", "cba",
                                        "z", true)))));

        // ---

        byte[] payload = archive(VariablesIT.class.getResource("variables").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), false);

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*x=123.*", ab);
        assertLog(".*y=abc.*", ab);
        assertLog(".*z=false.*", ab);
    }

    @Test(timeout = 30000)
    public void testCrypto() throws Exception {
        String projectName = "project_" + System.currentTimeMillis();
        String secretValue = "secret_" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName, null, null, null));

        EncryptValueResponse evr = projectResource.encrypt(projectName, new EncryptValueRequest(secretValue));
        String encryptedValue = DatatypeConverter.printBase64Binary(evr.getData());

        projectResource.updateConfiguration(projectName, ImmutableMap.of("arguments",
                ImmutableMap.of("mySecret", encryptedValue)));

        // ---

        byte[] payload = archive(VariablesIT.class.getResource("crypto").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), false);

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*" + secretValue + ".*", ab);
    }
}
