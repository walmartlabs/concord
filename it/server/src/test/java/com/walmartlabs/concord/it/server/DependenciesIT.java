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

import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DependenciesIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testUploadAndRun() throws Exception {
        String dep = "file:///" + ITConstants.DEPENDENCIES_DIR + "/example.jar";

        String request = "{ \"entryPoint\": \"main\", \"dependencies\": [ \"" + dep + "\" ] }";
        Path tmpDir = createTempDir();
        Path requestFile = tmpDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        Files.write(requestFile, Collections.singletonList(request));

        Path src = Paths.get(DependenciesIT.class.getResource("deps").toURI());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(baos)) {
            IOUtils.zip(zip, src);
            IOUtils.zip(zip, tmpDir);
        }

        byte[] payload = baos.toByteArray();

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        // ---

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello!.*", ab);
    }

    @Test(timeout = 180000)
    public void testMaven() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("mvnDeps").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, Concord.*", ab);
    }
}
