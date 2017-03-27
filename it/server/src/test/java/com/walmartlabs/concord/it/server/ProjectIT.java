package com.walmartlabs.concord.it.server;

import com.google.common.collect.Sets;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.CreateUserResponse;
import com.walmartlabs.concord.server.api.user.UserResource;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class ProjectIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        File tmpDir = Files.createTempDirectory("test").toFile();
        File src = new File(ProjectIT.class.getResource("project").toURI());
        IOUtils.copy(src.toPath(), tmpDir.toPath());

        Git repo = Git.init().setDirectory(tmpDir).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.getAbsolutePath();

        // ---

        String projectName = "myProject@" + System.currentTimeMillis();
        String userName = "myUser@" + System.currentTimeMillis();
        Set<String> permissions = Sets.newHashSet(
                String.format(Permissions.PROJECT_UPDATE_INSTANCE, projectName),
                String.format(Permissions.PROCESS_START_PROJECT, projectName));
        String repoName = "myRepo@" + System.currentTimeMillis();
        String repoUrl = gitUrl;
        String entryPoint = projectName + ":" + repoName + ":main";
        String greeting = "Hello, @" + System.currentTimeMillis();
        Map<String, Object> args = Collections.singletonMap(Constants.ARGUMENTS_KEY,
                Collections.singletonMap("greeting", greeting));

        // ---

        ProcessStatusResponse psr = doTest(projectName, null, userName, permissions, repoName, repoUrl, entryPoint, args);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*" + greeting + ".*", ab);
    }

    protected ProcessStatusResponse doTest(String projectName, Set<String> projectTemplates,
                                           String userName, Set<String> permissions,
                                           String repoName, String repoUrl,
                                           String entryPoint, Map<String, Object> args) throws InterruptedException, IOException {

        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr = projectResource.createOrUpdate(new CreateProjectRequest(projectName, projectTemplates, null));
        assertTrue(cpr.isOk());

        UserResource userResource = proxy(UserResource.class);
        CreateUserResponse cur = userResource.createOrUpdate(new CreateUserRequest(userName, permissions));
        assertTrue(cur.isOk());

        String userId = cur.getId();

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest(userId));
        assertTrue(cakr.isOk());

        String apiKey = cakr.getKey();

        // ---

        setApiKey(apiKey);

        CreateRepositoryResponse crr = projectResource.createRepository(projectName, new CreateRepositoryRequest(repoName, repoUrl, null, null));
        assertTrue(crr.isOk());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(entryPoint, args);
        assertTrue(spr.isOk());

        String instanceId = spr.getInstanceId();

        // ---

        ProcessStatusResponse psr = waitForCompletion(processResource, instanceId);
        assertTrue(psr.getStatus() == ProcessStatus.FINISHED);

        // ---

        return psr;
    }
}
