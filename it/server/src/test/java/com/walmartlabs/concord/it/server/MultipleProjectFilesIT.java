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

import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.PathUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class MultipleProjectFilesIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        Path template = zip(Paths.get(MultipleProjectFilesIT.class.getResource("multiProjectTemplate/template").toURI()));
        String templateUrl = "file://" + template.toAbsolutePath();

        // ---

        byte[] payload = archive(MultipleProjectFilesIT.class.getResource("multiProjectTemplate/user").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("template", templateUrl);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello, Concord!.*", ab);
    }

    private static Path zip(Path src) throws IOException {
        Path dst = PathUtils.createTempFile("template", ".zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(dst))) {
            IOUtils.zip(zip, src);
        }

        Set<PosixFilePermission> s = new HashSet<>();
        s.add(PosixFilePermission.OWNER_READ);
        s.add(PosixFilePermission.GROUP_READ);
        s.add(PosixFilePermission.OTHERS_READ);
        Files.setPosixFilePermissions(dst, s);

        return dst;
    }
}
