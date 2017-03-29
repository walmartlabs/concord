package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectRequest;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import com.walmartlabs.concord.server.template.TemplateConstants;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

public class TemplateIT extends AbstractServerIT {

    private static final String META_JS = "({ entryPoint: \"main\", arguments: { greeting: \"Hello, \" + _input.name }})";
    private static final String PROCESS_YAML = "main:\n- expr: ${log.info(\"test\", greeting)}";

    @Test
    public void test() throws Exception {
        String templateName = "template_" + System.currentTimeMillis();
        String projectName = "project_" + System.currentTimeMillis();
        String myName = "myName_" + System.currentTimeMillis();
        Path templatePath = createTemplate();

        // ---

        TemplateResource templateResource = proxy(TemplateResource.class);
        try (InputStream in = Files.newInputStream(templatePath)) {
            templateResource.create(templateName, in);
        }

        // ---

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new CreateProjectRequest(projectName, Collections.singleton(templateName), null));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, Collections.singletonMap("name", myName));

        // ---

        ProcessStatusResponse pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello, " + myName + ".*", ab);
    }

    private static Path createTemplate() throws IOException {
        Path tmpDir = Files.createTempDirectory("template");

        Path metaPath = tmpDir.resolve(TemplateConstants.REQUEST_DATA_TEMPLATE_FILE_NAME);
        Files.write(metaPath, META_JS.getBytes());

        Path processesPath = tmpDir.resolve(Constants.DEFINITIONS_DIR_NAME);
        Files.createDirectories(processesPath);

        Path procPath = processesPath.resolve("hello.yml");
        Files.write(procPath, PROCESS_YAML.getBytes());

        Path tmpZip = Files.createTempFile("template", "zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmpZip))) {
            IOUtils.zip(zip, tmpDir);
        }

        return tmpZip;
    }
}
