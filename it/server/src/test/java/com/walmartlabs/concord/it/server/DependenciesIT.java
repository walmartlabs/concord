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
import com.walmartlabs.concord.common.ZipUtils;
import com.walmartlabs.concord.it.common.ITConstants;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DependenciesIT extends AbstractServerIT {

    @Test
    public void testUploadAndRun() throws Exception {
        String dep = "file:///" + ITConstants.DEPENDENCIES_DIR + "/example.jar";

        String request = "{ \"entryPoint\": \"main\", \"dependencies\": [ \"" + dep + "\" ] }";
        Path tmpDir = createSharedTempDir();
        Path requestFile = tmpDir.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        Files.write(requestFile, Collections.singletonList(request));

        Path src = Paths.get(DependenciesIT.class.getResource("deps").toURI());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(baos)) {
            ZipUtils.zip(zip, src);
            ZipUtils.zip(zip, tmpDir);
        }

        byte[] payload = baos.toByteArray();

        // ---

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry psr = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        // ---

        byte[] ab = getLog(psr.getInstanceId());
        assertLog(".*Hello!.*", ab);
    }

    @Test
    public void testMaven() throws Exception {
        byte[] payload = archive(DependenciesIT.class.getResource("mvnDeps").toURI());

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    public void testExtraDependencies() throws Exception {
        byte[] payload = archive(DependenciesIT.class.getResource("extraDeps").toURI());

        // do the first run with the default profile

        StartProcessResponse spr = start(payload);
        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:2.15.0.*", ab);

        // then add "foo" profile

        spr = start(Map.of("archive", payload, "activeProfiles", "foo"));
        pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        ab = getLog(pir.getInstanceId());
        assertLog(".*mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:2.16.0.*", ab);
        assertNoLog(".*confluence-task.*", ab);

        // then both "foo" and "bar" profiles

        spr = start(Map.of("archive", payload, "activeProfiles", "foo,bar"));
        pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        ab = getLog(pir.getInstanceId());
        assertLog(".*mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:2.16.0.*", ab);
        assertLog(".*mvn://com.walmartlabs.concord.plugins:confluence-task:2.5.0.*", ab);
    }
}
