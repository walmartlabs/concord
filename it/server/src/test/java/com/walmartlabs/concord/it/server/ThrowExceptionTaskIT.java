package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThrowExceptionTaskIT extends AbstractServerIT {

    @Test
    public void testThrowException() throws Exception {
        URI uri = ThrowExceptionTaskIT.class.getResource("throwExceptionTask").toURI();
        byte[] payload = archive(uri);

        // start the process

        StartProcessResponse spr = start(payload);

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        // check logs
        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Catch that!.*", 3, ab);
    }

    @Test
    public void testThrowExceptionMessage() throws Exception {
        URI uri = ThrowExceptionTaskIT.class.getResource("throwExceptionMessage").toURI();
        byte[] payload = archive(uri);

        // start the process

        StartProcessResponse spr = start(payload);

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        // check logs
        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Kaboom!! Error occurred.*", 2, ab);
    }
}
