package com.walmartlabs.concord.it.server.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.it.server.AbstractServerIT;
import com.walmartlabs.concord.it.server.GitUtils;
import com.walmartlabs.concord.it.server.ITConstants;
import com.walmartlabs.concord.it.server.MockGitSshServer;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;
import com.walmartlabs.concord.server.api.security.secret.SecretResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.*;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @see "docs/examples/ansible_project"
 */
public class AnsibleProjectIT extends AbstractServerIT {

    private MockGitSshServer gitServer;
    private int gitPort;

    @Before
    public void setUp() throws Exception {
        Path data = Paths.get(AnsibleProjectIT.class.getResource("ansibleproject/git").toURI());
        Path repo = GitUtils.createBareRepository(data);

        gitServer = new MockGitSshServer(0, repo.toAbsolutePath().toString());
        gitServer.start();

        gitPort = gitServer.getPort();
    }

    @After
    public void tearDown() throws Exception {
        gitServer.stop();
    }

    @Test
    public void test() throws Exception {
        Map<String, InputStream> input = new HashMap<>();
        input.put("request", resource("ansibleproject/request.json"));
        input.put("inventory", resource("ansibleproject/inventory.ini"));
        test(input);
    }

    @Test
    public void testInlineInventory() throws Exception {
        Map<String, InputStream> input = new HashMap<>();
        input.put("request", resource("ansibleproject/requestInline.json"));
        test(input);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFailure() throws Exception {
        Map<String, InputStream> input = new HashMap<>();
        input.put("request", resource("ansibleproject/requestFailure.json"));

        // ---

        String templatePath = "file://" + ITConstants.DEPENDENCIES_DIR + "/ansible-template.jar";

        String projectName = "project@" + System.currentTimeMillis();
        String repoSecretName = "repoSecret@" + System.currentTimeMillis();
        String repoName = "repo@" + System.currentTimeMillis();
        String repoUrl = String.format(ITConstants.GIT_SERVER_URL_PATTERN, gitPort);
        String entryPoint = URLEncoder.encode(projectName + ":" + repoName, "UTF-8");

        // ---

        SecretResource secretResource = proxy(SecretResource.class);
        secretResource.createKeyPair(repoSecretName);

        // ---

        UpdateRepositoryRequest repo = new UpdateRepositoryRequest(repoUrl, "master", null, repoSecretName);
        ProjectResource projectResource = proxy(ProjectResource.class);
        Map<String, Object> cfg = Collections.singletonMap(Constants.Request.TEMPLATE_KEY, templatePath);
        projectResource.createOrUpdate(new ProjectEntry(projectName, null, singletonMap(repoName, repo), cfg));

        // ---

        StartProcessResponse spr = start(entryPoint, input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FAILED);

        // ---

        Response resp = processResource.downloadAttachment(spr.getInstanceId(), "ansible_stats.json");
        assertEquals(Status.OK.getStatusCode(), resp.getStatus());

        ObjectMapper om = new ObjectMapper();
        Map<String, Object> stats = om.readValue(resp.readEntity(InputStream.class), Map.class);
        resp.close();

        Collection<String> failures = (Collection<String>) stats.get("failures");
        assertNotNull(failures);
        assertEquals(1, failures.size());
        assertEquals("128.0.0.1", failures.iterator().next());
    }

    @SuppressWarnings("unchecked")
    public void test(Map<String, InputStream> input) throws Exception {
        String templatePath = "file://" + ITConstants.DEPENDENCIES_DIR + "/ansible-template.jar";

        String projectName = "project@" + System.currentTimeMillis();
        String repoSecretName = "repoSecret@" + System.currentTimeMillis();
        String repoName = "repo@" + System.currentTimeMillis();
        String repoUrl = String.format(ITConstants.GIT_SERVER_URL_PATTERN, gitPort);
        String entryPoint = URLEncoder.encode(projectName + ":" + repoName, "UTF-8");

        // ---

        SecretResource secretResource = proxy(SecretResource.class);
        secretResource.createKeyPair(repoSecretName);

        // ---

        UpdateRepositoryRequest repo = new UpdateRepositoryRequest(repoUrl, "master", null, repoSecretName);
        ProjectResource projectResource = proxy(ProjectResource.class);

        Map<String, Object> cfg = Collections.singletonMap(Constants.Request.TEMPLATE_KEY, templatePath);
        projectResource.createOrUpdate(new ProjectEntry(projectName, null, singletonMap(repoName, repo), cfg));

        // ---

        StartProcessResponse spr = start(entryPoint, input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry psr = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*\"msg\":.*Hello, world.*", ab);

        // check if `force_color` is working
        assertLogAtLeast(".*\\[0;32m.*", 3, ab);

        // ---

        Response resp = processResource.downloadAttachment(spr.getInstanceId(), "ansible_stats.json");
        assertEquals(Status.OK.getStatusCode(), resp.getStatus());

        ObjectMapper om = new ObjectMapper();
        Map<String, Object> stats = om.readValue(resp.readEntity(InputStream.class), Map.class);
        resp.close();

        Collection<String> oks = (Collection<String>) stats.get("ok");
        assertNotNull(oks);
        assertEquals(1, oks.size());
        assertEquals("127.0.0.1", oks.iterator().next());
    }

    private static InputStream resource(String path) {
        return AnsibleProjectIT.class.getResourceAsStream(path);
    }
}
