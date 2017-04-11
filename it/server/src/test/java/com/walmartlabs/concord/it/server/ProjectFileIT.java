package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.project.ProjectDirectoryLoader;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ProjectFileIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testSingleProfile() throws Exception {
        simpleTest("projectfile/singleprofile");
    }

    @Test(timeout = 30000)
    public void testOverrideFlow() throws Exception {
        simpleTest("projectfile/overrideflow");
    }

    @Test(timeout = 30000)
    public void testExpressionsInVariables() throws Exception {
        simpleTest("projectfile/expr");
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

            Path p = tmpDir.resolve(ProjectDirectoryLoader.PROJECT_FILE_NAME);
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
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload));
        assertNotNull(spr.getInstanceId());

        ProcessStatusResponse psr = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, psr.getStatus());

        // ---

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello!.*", ab);
    }

    private void simpleTest(String resource) throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource(resource).toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload));
        assertNotNull(spr.getInstanceId());

        ProcessStatusResponse pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
    }
}
