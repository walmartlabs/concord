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
import com.walmartlabs.concord.client2.ProcessListFilter;
import com.walmartlabs.concord.common.IOUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExternalImportsIT extends AbstractServerIT {

    @Test
    public void testExternalImportWithForm() throws Exception {
        String repoUrl = "file://" + initRepo("externalImportWithForm");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMain", repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for suspend

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.listProcessForms(pir.getInstanceId());
        assertEquals(1, forms.size());

        formsApi.submitForm(pir.getInstanceId(), forms.get(0).getName(), Collections.singletonMap("name", "boo"));

        // wait process finished
        pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template, Concord!.*", ab);
        assertLog(".*Template form submitted: boo.*", ab);
    }

    @Test
    public void testExternalImportWithDefaults() throws Exception {
        String repoUrl = "file://" + initRepo("externalImport");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMain", repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template, Concord!.*", ab);
    }

    @Test
    public void testExternalImportWithPath() throws Exception {
        String repoUrl = "file://" + initRepo("externalImportWithDir");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainWithPath", repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template DIR, Concord!.*", ab);
    }

    @Test
    public void testExternalImportWithConfigurationInImport() throws Exception {
        String repoUrl = "file://" + initRepo("externalImportWithConfiguration");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMain", repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template, Concord!.*", ab);
    }

    // payload with concord/concord.yml and import with concord/concord.yml,
    // concord.yml from import will use.
    @Test
    public void testExternalImportWithConcordDirReplace() throws Exception {
        String repoUrl = "file://" + initRepo("externalImport");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainWithFlow", repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello from Template, Concord!.*", ab);
    }

    @Test
    public void testExternalImportWithOnFailure() throws Exception {
        String repoUrl = initRepo("externalImportFailHandler");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainFailed", "file://" + repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        ProcessEntry child = waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.FAILURE_HANDLER, ProcessEntry.StatusEnum.FINISHED);

        // check the logs

        byte[] ab = getLog(child.getInstanceId());

        assertLog(".*oh, handled.*", ab);
    }

    @Test
    public void testExternalImportWithForks() throws Exception {
        String repoUrl = "file://" +  initRepo("externalImportWithForks");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainWithForks", repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        ProcessEntry child = waitForChild(processApi, pir.getInstanceId(), ProcessEntry.KindEnum.DEFAULT, ProcessEntry.StatusEnum.FINISHED);

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());
        byte[] cd = getLog(child.getInstanceId());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Concord, imports!.*", cd);
    }

    @Test
    public void testExternalImportValidation() throws Exception {
        String importRepoUrl = initRepo("externalImport");

        String userRepoUrl = initRepo("externalImportTriggerReference");
        replace(Paths.get(userRepoUrl, "concord.yml"), "{{gitUrl}}", "file://" + importRepoUrl);
        commit(Paths.get(userRepoUrl).toFile());

        // ---

        assertExternalImportValidation("file://" + userRepoUrl);
    }

    @Test
    public void testExternalImportWithSymlink() throws Exception {
        // Init repo and add symlink
        String importRepoUrl = initRepo("externalImportSymlink", importRepo -> {
            try {
                Path target = importRepo.resolve("concord.txt");
                Path linkDir = Files.createDirectory(importRepo.resolve("link_dir"));
                Path link = linkDir.resolve("concord.yml");
                Files.createSymbolicLink(link, linkDir.relativize(target));
            } catch (IOException e) {
                fail("Error while creating symlink");
            }
        });

        String userRepoUrl = initRepo("externalImportTriggerReference");
        replace(Paths.get(userRepoUrl, "concord.yml"), "{{gitUrl}}", "file://" + importRepoUrl);
        commit(Paths.get(userRepoUrl).toFile());

        // ---

        assertExternalImportValidation("file://" + userRepoUrl);
    }

    private void assertExternalImportValidation(String userRepoUrl) throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "prj_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url("file://" + userRepoUrl)
                        .branch("master"))));

        RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
        GenericOperationResult refreshResp = repositoriesApi.refreshRepository(orgName, projectName, repoName, true);
        assertTrue(refreshResp.getOk());
        RepositoryValidationResponse resp = repositoriesApi.validateRepository(orgName, projectName, repoName);
        assertTrue(resp.getOk());
    }

    @Test
    public void testExternalImportWithExcludeFullDir() throws Exception {
        String repoUrl = "file://" +  initRepo("externalImportWithDir");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainWithExclude", repoUrl, "dir");
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template, Concord!.*", ab);
    }

    @Test
    public void testExternalImportWithExcludeFileFromDir() throws Exception {
        String repoUrl = "file://" + initRepo("externalImportWithDir");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainWithExclude", repoUrl, "dir/concord.yml");
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template, Concord!.*", ab);
    }

    @Test
    public void testExternalImportWithExcludeFile() throws Exception {
        String repoUrl = initRepo("externalImportWithDir");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainWithExclude", "file://" + repoUrl, "concord.yml");
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello from Template DIR, Concord!.*", ab);
    }

    @Test
    public void testImportWithTriggers() throws Exception {
        String importRepoUrl = initRepo("testTrigger");
        String clientRepoUrl = initRepo("importATrigger", "concord.yml", "{{gitUrl}}", "file://" + importRepoUrl);

        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url("file://" + clientRepoUrl)
                        .branch("master"))));

        // ---

        TriggersApi triggersApi = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> triggers = triggersApi.listTriggers(orgName, projectName, repoName);
            if (triggers != null && triggers.size() == 1 && triggers.get(0).getEventSource().equals("test")) {
                break;
            }

            Thread.sleep(1000);
        }

        // ---

        ExternalEventsApi externalEventsApi = new ExternalEventsApi(getApiClient());
        externalEventsApi.externalEvent("test", Collections.emptyMap());

        // ---

        ProcessEntry pe;

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .build();

        while (true) {
            List<ProcessEntry> l = processApi.listProcesses(filter);

            Optional<ProcessEntry> o = l.stream().filter(e -> e.getTriggeredBy().getTrigger().getEventSource().equals("test")).findFirst();
            if (o.isPresent()) {
                pe = o.get();
                break;
            }

            Thread.sleep(1000);
        }

        // ---

        waitForCompletion(getApiClient(), pe.getInstanceId());

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    public void testDependencyMerging() throws Exception {
        String repoUrl = initRepo("externalImportWithDeps");

        Path payloadDir = createPayload("externalImportMainWithDeps", "file://" + repoUrl, "concord.yml");
        byte[] payload = archive(payloadDir.toUri());

        // ---

        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello from Groovy!.*", ab);
        assertLog(".*Hello from Python!.*", ab);
    }

    @Test
    public void testExternalImportState() throws Exception {
        String repoUrl = initRepo("externalImportWithDir");

        // prepare the payload
        Path payloadDir = createPayload("externalImportMainStateTest", "file://" + repoUrl);
        byte[] payload = archive(payloadDir.toUri());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());
        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.ENQUEUED);

        try {
            processApi.downloadStateFile(spr.getInstanceId(), "import_data/concord.yml");
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }

        processApi.kill(spr.getInstanceId());
    }

    @Test
    public void testGitImportWithCommitAsVersion() throws Exception {
        String repoUrl = initRepo("externalImport");
        Git repo = Git.open(new File(repoUrl));
        String commitId = repo.log().setMaxCount(1).call().iterator().next().name();
        // add new commitId:
        Files.write(Paths.get(repoUrl).resolve("concord.yml"), "trash".getBytes());
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("up").call();

        // prepare the payload
        Map<String, String> replacements = new HashMap<>();
        replacements.put("{{gitUrl}}", "file://" + repoUrl);
        replacements.put("{{version}}", commitId);
        Path payloadDir = createPayload("externalImportMainWithVersion", replacements);
        byte[] payload = archive(payloadDir.toUri());

        // ---

        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello from Template, Concord!.*", ab);
    }

    private static String initRepo(String resourceName) throws Exception {
        return initRepo(resourceName, null, null, null);
    }

    private static String initRepo(String resourceName, Consumer<Path> fileAdder) throws Exception {
        return initRepo(resourceName, null, null, null, fileAdder);
    }

    private static String initRepo(String resourceName, String path, String find, String replace) throws Exception {
        return initRepo(resourceName, path, find, replace, p -> {});
    }

    private static String initRepo(String resourceName, String path, String find, String replace, Consumer<Path> fileAdder) throws Exception {

        Path tmpDir = createTempDir();

        File src = new File(ExternalImportsIT.class.getResource(resourceName).toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        // add more files
        fileAdder.accept(tmpDir);

        if (path != null) {
            Path p = tmpDir.resolve(path);
            String s = new String(Files.readAllBytes(p));
            Files.write(p, s.replace(find, replace).getBytes());
        }

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
            repo.branchCreate().setName("main").call();
            return tmpDir.toAbsolutePath().toString();
        }
    }

    private static Path createPayload(String resourceName, String repoUrl) throws Exception {
        return createPayload(resourceName, Collections.singletonMap("{{gitUrl}}", repoUrl));
    }

    private static Path createPayload(String resourceName, String repoUrl, String exclude) throws Exception {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("{{gitUrl}}", repoUrl);
        replacements.put("{{exclude}}", exclude);
        return createPayload(resourceName, replacements);
    }

    private static Path createPayload(String resourceName, Map<String, String> replacements) throws Exception {
        Path tmpDir = createTempDir();
        File src = new File(ExternalImportsIT.class.getResource(resourceName).toURI());
        IOUtils.copy(src.toPath(), tmpDir);
        Path concordFile = tmpDir.resolve("concord.yml");
        replacements.forEach((k, v) -> replace(concordFile, k, v));
        return tmpDir;
    }

    private static void replace(Path concord, String what, String newValue) {
        try {
            List<String> fileContent = Files.readAllLines(concord, StandardCharsets.UTF_8).stream()
                    .map(l -> l.replaceAll(Pattern.quote(what), newValue))
                    .collect(Collectors.toList());

            Files.write(concord, fileContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void commit(File dir) throws Exception {
        Git repo = Git.open(dir);
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();
    }
}
