package com.walmartlabs.concord.it.server;

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class CryptoIT extends AbstractServerIT {

    @Test
    public void testPlain() throws Exception {
        String teamName = "Default";

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();
        String storePassword = "store@" + randomString();

        addPlainSecret(teamName, secretName, false, storePassword, secretValue.getBytes());

        // ---

        test("cryptoPlain", secretName, storePassword, ".*value=" + secretValue + ".*");
    }

    @Test
    public void testUsernamePassword() throws Exception {
        String teamName = "Default";

        // ---

        String secretName = "secret@" + randomString();
        String secretUsername = "username@" + randomString();
        String secretPassword = "password@" + randomString();
        String storePassword = "store@" + randomString();

        addUsernamePassword(teamName, secretName, false, storePassword, secretUsername, secretPassword);

        // ---

        test("cryptoPwd", secretName, storePassword, ".*" + secretUsername + " " + secretPassword + ".*");
    }

    private void test(String project, String secretName, String storePassword, String log) throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource(project).toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.pwd", storePassword));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(log, ab);
    }
}
