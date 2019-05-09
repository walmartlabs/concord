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
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class ExternalImportsIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testExternalImportWithDefaults() throws Exception {
        String repoUrl = initRepo("externalImport");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMain", repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template, Concord!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testExternalImportWithPath() throws Exception {
        String repoUrl = initRepo("externalImportWithDir");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainWithPath", repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template DIR, Concord!.*", ab);
    }

    private static String initRepo(String resourceName) throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ExternalImportsIT.class.getResource(resourceName).toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();
        return tmpDir.toAbsolutePath().toString();
    }

    private static Path createPayload(String resourceName, String repoUrl) throws Exception {
        Path tmpDir = createTempDir();
        File src = new File(ExternalImportsIT.class.getResource(resourceName).toURI());
        IOUtils.copy(src.toPath(), tmpDir);
        Path concordFile = tmpDir.resolve("concord.yml");
        replace(concordFile, "{{gitUrl}}", repoUrl);
        return tmpDir;
    }

    private static void replace(Path concord, String what, String newValue) throws IOException {
        List<String> fileContent = Files.readAllLines(concord, StandardCharsets.UTF_8).stream()
                .map(l -> l.replaceAll(Pattern.quote(what), newValue))
                .collect(Collectors.toList());

        Files.write(concord, fileContent, StandardCharsets.UTF_8);
    }
}
