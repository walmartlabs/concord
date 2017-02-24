package com.walmartlabs.concord.it.server.docs.ansibleproject;

import com.walmartlabs.concord.it.server.AbstractServerIT;
import com.walmartlabs.concord.it.server.GitUtils;
import com.walmartlabs.concord.it.server.ITConstants;
import com.walmartlabs.concord.it.server.MockGitSshServer;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectRequest;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;
import com.walmartlabs.concord.server.api.security.secret.SecretResource;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;

/**
 * @see "docs/examples/ansible_project"
 */
public class AnsibleProjectIT extends AbstractServerIT {

    private MockGitSshServer gitServer;

    @Before
    public void setUp() throws Exception {
        Path data = Paths.get(AnsibleProjectIT.class.getResource("git").toURI());
        Path repo = GitUtils.createBareRepository(data);

        gitServer = new MockGitSshServer(ITConstants.GIT_SERVER_PORT, repo.toAbsolutePath().toString());
        gitServer.start();
    }

    @After
    public void tearDown() throws Exception {
        gitServer.stop();
    }

    @Test
    public void test() throws Exception {
        String templateName = "template@" + System.currentTimeMillis();
        String projectName = "project@" + System.currentTimeMillis();
        String repoSecretName = "repoSecret@" + System.currentTimeMillis();
        String repoName = "repo@" + System.currentTimeMillis();
        String repoUrl = ITConstants.GIT_SERVER_URL;
        String entryPoint = URLEncoder.encode(projectName + ":" + repoName, "UTF-8");

        // ---

        TemplateResource templateResource = proxy(TemplateResource.class);
        templateResource.create(templateName, fsResource(ITConstants.TEMPLATES_DIR + "/ansible-template.zip"));

        // ---

        SecretResource secretResource = proxy(SecretResource.class);
        secretResource.createKeyPair(repoSecretName);

        // ---

        UpdateRepositoryRequest repo = new UpdateRepositoryRequest(repoUrl, "master", repoSecretName);
        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.create(new CreateProjectRequest(projectName, singleton(templateName), singletonMap(repoName, repo)));

        // ---

        Map<String, InputStream> input = new HashMap<>();
        input.put("request", resource("request.json"));
        input.put("inventory", resource("inventory.ini"));
        StartProcessResponse spr = start(entryPoint, input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessStatusResponse psr = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(psr);
        assertLog(".*\"msg\":.*Hello, world.*", ab);

        // check if `force_color` is working
        assertLog(".*\\[0;32m.*", 3, ab);
    }

    private static InputStream resource(String path) {
        return AnsibleProjectIT.class.getResourceAsStream(path);
    }

    private static InputStream fsResource(String path) throws FileNotFoundException {
        return new FileInputStream(path);
    }
}
