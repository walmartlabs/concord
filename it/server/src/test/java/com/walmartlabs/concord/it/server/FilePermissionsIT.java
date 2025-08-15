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
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.ZipUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FilePermissionsIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        Path src = Paths.get(FilePermissionsIT.class.getResource("filePerm").toURI());

        Path tmpDir = createTempDir();
        IOUtils.copy(src, tmpDir);

        Path testFile = tmpDir.resolve("test.sh");
        Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(testFile));
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(testFile, permissions);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(payload)) {
            ZipUtils.zip(zip, tmpDir);
        }

        // ---

        StartProcessResponse spr = start(payload.toByteArray());
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello!.*", ab);
    }
}
