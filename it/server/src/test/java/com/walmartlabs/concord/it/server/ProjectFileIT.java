package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;
import com.walmartlabs.concord.server.api.security.secret.SecretResource;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.*;

public class ProjectFileIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testSingleProfile() throws Exception {
        simpleTest("projectfile/singleprofile", ".*Hello, world.*", ".*54321.*");
    }

    @Test(timeout = 30000)
    public void testExternalProfile() throws Exception {
        simpleTest("projectfile/externalprofile", ".*Hello, world.*");
    }

    @Test(timeout = 30000)
    public void testAltName() throws Exception {
        simpleTest("projectfile/altname", ".*Hello, world.*");
    }

    @Test(timeout = 30000)
    public void testOverrideFlow() throws Exception {
        simpleTest("projectfile/overrideflow", ".*Hello, world.*");
    }

    @Test(timeout = 30000)
    public void testExpressionsInVariables() throws Exception {
        simpleTest("projectfile/expr");
    }

    @Test(timeout = 30000)
    public void testExternalScript() throws Exception {
        simpleTest("projectfile/externalscript", ".*hello!.*", ".*bye!.*");
    }

    @Test(timeout = 30000)
    public void testDependencies() throws Exception {
        String dep = "file:///" + ITConstants.DEPENDENCIES_DIR + "/example.jar";
        Path tmpDir = Files.createTempDirectory("test");

        // prepare .concord.yml
        try (InputStream in = ProjectFileIT.class.getResourceAsStream("projectfile/deps/.template.yml");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

            List<String> l = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                l.add(line.replaceAll("WILL_BE_REPLACED", dep));
            }

            Path p = tmpDir.resolve(ProjectLoader.PROJECT_FILE_NAME);
            Files.write(p, l);
        }

        // create the payload

        String request = "{ \"entryPoint\": \"main\" }";
        Path requestFile = tmpDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        Files.write(requestFile, Arrays.asList(request));

        Path src = Paths.get(DependenciesIT.class.getResource("projectfile/deps").toURI());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            IOUtils.zip(zip, src);
            IOUtils.zip(zip, tmpDir);
        }

        byte[] payload = baos.toByteArray();

        // send the request

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry psr = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, psr.getStatus());

        // ---

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello!.*", ab);
    }

    @Test
    public void testArchiveOverride() throws Exception {
        String projectName = "project_" + System.currentTimeMillis();
        String repoName = "repo_" + System.currentTimeMillis();
        String repoUrl = "git@test_" + System.currentTimeMillis();
        String secretName = "secret_" + System.currentTimeMillis();

        SecretResource secretResource = proxy(SecretResource.class);
        secretResource.createKeyPair(secretName);

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName, null,
                Collections.singletonMap(repoName, new UpdateRepositoryRequest(repoUrl, "master", null, null, secretName)), null));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("projectfile/singleprofile").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*54321.*", ab);
    }

    @Test
    public void testArchiveOverrideSync() throws Exception {
        String projectName = "project_" + System.currentTimeMillis();
        String repoName = "repo_" + System.currentTimeMillis();
        String repoUrl = "git@test_" + System.currentTimeMillis();
        String secretName = "secret_" + System.currentTimeMillis();

        SecretResource secretResource = proxy(SecretResource.class);
        secretResource.createKeyPair(secretName);

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName, null,
                Collections.singletonMap(repoName, new UpdateRepositoryRequest(repoUrl, "master", null, null, secretName)), null));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("projectfile/singleprofile-sync").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), true);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = processResource.get(spr.getInstanceId());

        // ---
        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*100223.*", ab);
        assertLog(".*Boo Zoo.*", ab);
        assertLog(".*1000022.*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*red.*", ab);

        assertTrue(pir.getStatus() == ProcessStatus.FINISHED);
    }

    private void simpleTest(String resource, String... logPatterns) throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource(resource).toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        if (logPatterns == null || logPatterns.length == 0) {
            return;
        }

        byte[] ab = getLog(pir.getLogFileName());

        for (String p : logPatterns) {
            assertLog(p, ab);
        }
    }
}
