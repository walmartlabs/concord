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
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class SerializationIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        byte[] payload = archive(SerializationIT.class.getResource("serialization").toURI(),
                ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        assertNotNull(spr.getInstanceId());

        // ---

        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        // ---

        FormResource formResource = proxy(FormResource.class);
        List<FormListEntry> forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        formResource.submit(spr.getInstanceId(), f.getFormInstanceId(),
                Collections.singletonMap("y", "hello"));

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*hello.*", ab);
    }
}
