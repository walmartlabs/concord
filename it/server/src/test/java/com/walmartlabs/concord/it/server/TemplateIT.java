package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.project.TemplateAliasEntry;
import com.walmartlabs.concord.server.api.project.TemplateAliasResource;
import com.walmartlabs.concord.server.process.pipelines.processors.TemplateScriptProcessor;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class TemplateIT extends AbstractServerIT {

    private static final String META_JS = "({ entryPoint: \"main\", arguments: { greeting: \"Hello, \" + _input.name }})";
    private static final String PROCESS_YAML = "main:\n- expr: ${log.info(\"test\", greeting)}";

    @Test(timeout = 30000)
    public void test() throws Exception {
        String templateAlias = "template_" + randomString();
        Path templatePath = createTemplate();

        TemplateAliasResource templateAliasResource = proxy(TemplateAliasResource.class);
        templateAliasResource.createOrUpdate(new TemplateAliasEntry(templateAlias, templatePath.toUri().toString()));

        // ---

        String projectName = "project_" + randomString();
        String myName = "myName_" + randomString();

        // ---

        ProjectResource projectResource = proxy(ProjectResource.class);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(InternalConstants.Request.TEMPLATE_KEY, templateAlias);
        projectResource.createOrUpdate(new ProjectEntry(null, projectName, null, null, null, null, cfg, null, null));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, Collections.singletonMap("name", myName), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello, " + myName + ".*", ab);
    }

    private static Path createTemplate() throws IOException {
        Path tmpDir = Files.createTempDirectory("template");

        Path metaPath = tmpDir.resolve(TemplateScriptProcessor.REQUEST_DATA_TEMPLATE_FILE_NAME);
        Files.write(metaPath, META_JS.getBytes());

        Path processesPath = tmpDir.resolve(InternalConstants.Files.DEFINITIONS_DIR_NAME);
        Files.createDirectories(processesPath);

        Path procPath = processesPath.resolve("hello.yml");
        Files.write(procPath, PROCESS_YAML.getBytes());

        Path tmpZip = Files.createTempFile("template", ".zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmpZip))) {
            IOUtils.zip(zip, tmpDir);
        }

        return tmpZip;
    }
}
