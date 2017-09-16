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
        String secretName = "secret@" + System.currentTimeMillis();
        String secretValue = "value@" + System.currentTimeMillis();
        String storePassword = "store@" + System.currentTimeMillis();

        addPlainSecret(secretName, false, storePassword, secretValue.getBytes());

        // ---

        test("cryptoPlain", secretName, storePassword, ".*value=" + secretValue + ".*");
    }

    @Test
    public void testUsernamePassword() throws Exception {
        String secretName = "secret@" + System.currentTimeMillis();
        String secretUsername = "username@" + System.currentTimeMillis();
        String secretPassword = "username@" + System.currentTimeMillis();
        String storePassword = "store@" + System.currentTimeMillis();

        addUsernamePassword(secretName, false, storePassword, secretUsername, secretPassword);

        // ---

        test("cryptoPwd", secretName, storePassword, ".*value=.*" + secretUsername + ".*" + secretPassword + ".*");
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
