package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;

public class FailureHandlingIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testFailure() throws Exception {
        ProcessResource processResource = proxy(ProcessResource.class);

        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("failureHandling").toURI());

        // start the process and wait for it to fail

        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FAILED);

        // check the logs for the error message

        byte[] ab = getLog(pir.getLogFileName());
        assertLogAtLeast(".*boom!.*", 1, ab);

        // find the child processes

        ProcessEntry child = waitForChild(processResource, spr.getInstanceId(), ProcessKind.FAILURE_HANDLER, ProcessStatus.FINISHED);

        // check the logs for the successful message

        ab = getLog(child.getLogFileName());
        assertLog(".*hello!.*", ab);
    }

    @Test(timeout = 30000)
    public void testFailureHandlingError() throws Exception {
        ProcessResource processResource = proxy(ProcessResource.class);
        byte[] payload = archive(ProcessIT.class.getResource("failureHandlingError").toURI());

        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FAILED);

        // find the child processes

        ProcessEntry child = waitForChild(processResource, spr.getInstanceId(), ProcessKind.FAILURE_HANDLER, ProcessStatus.FAILED);
    }

    @Test(timeout = 30000)
    public void testCancel() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("cancelHandling").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.RUNNING);

        // cancel the running process

        processResource.kill(spr.getInstanceId());
        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.CANCELLED);

        // find the child processes

        ProcessEntry child = waitForChild(processResource, spr.getInstanceId(), ProcessKind.CANCEL_HANDLER, ProcessStatus.FINISHED);

        // check the logs for the successful message

        byte[] ab = getLog(child.getLogFileName());
        assertLog(".*abc!.*", ab);
    }

    @Test(timeout = 30000)
    public void testCancelSuspended() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("cancelSuspendHandling").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        // cancel the running process

        processResource.kill(spr.getInstanceId());
        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.CANCELLED);

        // find the child processes

        ProcessEntry child = waitForChild(processResource, spr.getInstanceId(), ProcessKind.CANCEL_HANDLER, ProcessStatus.FINISHED);

        // check the logs for the successful message

        byte[] ab = getLog(child.getLogFileName());
        assertLog(".*!cancelled by a user!.*", ab);
    }
}
