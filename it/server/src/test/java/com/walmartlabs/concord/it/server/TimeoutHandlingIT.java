package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;

@Execution(ExecutionMode.SAME_THREAD)
public class TimeoutHandlingIT extends AbstractServerIT {

    @Test
    public void testTimeout() throws Exception {
        ProcessApi processApi = new ProcessApi(getApiClient());

        // prepare the payload

        byte[] payload = archive(TimeoutHandlingIT.class.getResource("timeoutHandling").toURI());

        // start the process and wait for it to fail

        StartProcessResponse spr = start(payload);

        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.TIMED_OUT);

        // find the child processes

        ProcessEntry child = waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.TIMEOUT_HANDLER, ProcessEntry.StatusEnum.FINISHED);

        // check the handler's logs for expected messages

        byte[] ab = getLog(child.getInstanceId());
        assertLog(".*projectInfo: \\{.*orgName=Default.*\\}.*", ab);
        assertLog(".*processInfo: \\{.*sessionKey=.*\\}.*", ab);
        assertLog(".*initiator: \\{.*username=.*\\}.*", ab);
    }
}
