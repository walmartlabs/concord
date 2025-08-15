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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.ZipUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;

public class TemplateIT extends AbstractServerIT {

    private static final String MAIN_JS = "({ entryPoint: \"main\", arguments: { greeting: \"Hello, \" + _input.name }})";

    @Test
    public void test() throws Exception {
        final String processYml = "main:\n- expr: ${log.info(\"test\", greeting)}";

        String templateAlias = "template_" + randomString();
        Path templatePath = createTemplate(processYml, MAIN_JS);

        TemplateAliasApi templateAliasResource = new TemplateAliasApi(getApiClient());
        templateAliasResource.createOrUpdateTemplate(new TemplateAliasEntry()
                .alias(templateAlias)
                .url(templatePath.toUri().toString()));

        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String myName = "myName_" + randomString();

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.TEMPLATE_KEY, templateAlias);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .cfg(cfg)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("name", myName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello, " + myName + ".*", ab);
    }

    @Test
    public void testInputVariablesStillPresent() throws Exception {
        final String processYml = "main:\n- expr: ${log.info(\"test\", xxx)}";

        String templateAlias = "template_" + randomString();
        Path templatePath = createTemplate(processYml, MAIN_JS);

        TemplateAliasApi templateAliasResource = new TemplateAliasApi(getApiClient());
        templateAliasResource.createOrUpdateTemplate(new TemplateAliasEntry()
                .alias(templateAlias)
                .url(templatePath.toUri().toString()));

        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String myName = "myName_" + randomString();

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.TEMPLATE_KEY, templateAlias);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .cfg(cfg)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---
        Map<String, Object> args = Collections.singletonMap(Constants.Request.ARGUMENTS_KEY,
                Collections.singletonMap("xxx", "BOO"));

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("name", myName);
        input.put("request", args);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*BOO.*", ab);
    }

    @Test
    public void testEntryPointReference() throws Exception {
        final String processYml = "fromTemplate:\n- log: \"hello!\"";
        Path templatePath = createTemplate(processYml, null);

        Path tmpDir = createTempDir();

        File src = new File(TemplateIT.class.getResource("repositoryValidationTemplateRef").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Path concordYml = tmpDir.resolve("concord.yml");
        String s = new String(Files.readAllBytes(concordYml))
                .replace("{{ template }}", "file://" + templatePath.toAbsolutePath().toString());
        Files.write(concordYml, s.getBytes());

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        // ---

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));

        // ---

        RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
        RepositoryValidationResponse resp = repositoriesApi.validateRepository(orgName, projectName, repoName);
        assertTrue(resp.getOk());
        assertFalse(resp.getWarnings().isEmpty());
    }

    private static Path createTemplate(String process, String mainJs) throws IOException {
        Path tmpDir = createTempDir();

        if (mainJs != null) {
            Path metaPath = tmpDir.resolve("_main.js");
            Files.write(metaPath, mainJs.getBytes());
        }

        Path processesPath = tmpDir.resolve("flows");
        Files.createDirectories(processesPath);

        Path procPath = processesPath.resolve("hello.yml");
        Files.write(procPath, process.getBytes());

        Path tmpZip = createTempFile(".zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmpZip))) {
            ZipUtils.zip(zip, tmpDir);
        }

        return tmpZip;
    }
}
