package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.server.api.process.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;

public class FailureHandlingIT extends AbstractServerIT {

    @Test(timeout = 60000)
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

    @Test(timeout = 60000)
    public void testFailureHandlingError() throws Exception {
        ProcessResource processResource = proxy(ProcessResource.class);
        byte[] payload = archive(ProcessIT.class.getResource("failureHandlingError").toURI());

        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FAILED);

        // find the child processes

        ProcessEntry child = waitForChild(processResource, spr.getInstanceId(), ProcessKind.FAILURE_HANDLER, ProcessStatus.FAILED);
    }

    @Test(timeout = 60000)
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

    @Test(timeout = 60000)
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
