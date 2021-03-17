package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.client.*;
import org.junit.Test;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;

public class TimeoutHandlingIT extends AbstractServerIT {
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testTimeout() throws Exception {
        ProcessApi processApi = new ProcessApi(getApiClient());

        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("timeoutHandling").toURI());

        // start the process and wait for it to fail

        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.TIMED_OUT);

        // find the child processes

        ProcessEntry child = waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.TIMEOUT_HANDLER, ProcessEntry.StatusEnum.FINISHED);

        // check the handler's logs for expected messages

        byte[] ab = getLog(child.getLogFileName());
        assertLog(".*projectInfo: \\{.*orgName=Default.*\\}.*", ab);
        assertLog(".*processInfo: \\{.*sessionKey=.*\\}.*", ab);
        assertLog(".*initiator: \\{.*username=.*\\}.*", ab);
    }
}
