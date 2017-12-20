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

import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.Assert.fail;

public class RawPayloadProjectIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testReject() throws Exception {
        ProjectResource projectResource = proxy(ProjectResource.class);

        String orgName = "Default";
        String projectName = "project_" + System.currentTimeMillis();
        projectResource.createOrUpdate(orgName, new ProjectEntry(null, projectName, null, null, null, null, null, null, null, false));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        try {
            processResource.start(projectName, new ByteArrayInputStream(payload), null, false, null);
            fail("should fail");
        } catch (BadRequestException e) {
        }
    }
}
