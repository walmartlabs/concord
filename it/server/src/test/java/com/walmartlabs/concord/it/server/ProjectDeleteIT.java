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

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.api.project.DeleteProjectResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.*;

public class ProjectDeleteIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        String projectName = "project_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr = projectResource.createOrUpdate(new ProjectEntry(projectName));
        assertTrue(cpr.isOk());

        // ---

        byte[] payload = archive(ProjectDeleteIT.class.getResource("simple").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pe = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pe.getStatus());
        assertEquals(cpr.getId(), pe.getProjectId());

        // ---

        DeleteProjectResponse dpr = projectResource.delete(projectName);
        assertTrue(dpr.isOk());

        try {
            projectResource.get(projectName);
            fail("Should fail");
        } catch (NotFoundException | BadRequestException e) {
        }

        // ---

        pe = processResource.get(spr.getInstanceId());
        assertNull(pe.getProjectId());
    }
}
