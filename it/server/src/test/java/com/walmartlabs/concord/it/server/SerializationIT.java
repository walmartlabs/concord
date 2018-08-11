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



import com.walmartlabs.concord.client.*;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SerializationIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        byte[] payload = archive(SerializationIT.class.getResource("serialization").toURI(),
                ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // ---

        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        formsApi.submit(spr.getInstanceId(), f.getName(),
                Collections.singletonMap("y", "hello"));

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*hello.*", ab);
    }
}
