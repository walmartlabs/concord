package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectRequest;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;
import com.walmartlabs.concord.server.api.security.secret.SecretResource;
import com.walmartlabs.concord.server.api.security.secret.UsernamePasswordRequest;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;

@Ignore
public class AnsibleGitProjectIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        String templateName = "template@" + System.currentTimeMillis();
        String projectName = "project@" + System.currentTimeMillis();
        String repoSecretName = "repoSecret@" + System.currentTimeMillis();
        String repoName = "repo@" + System.currentTimeMillis();
        String repoUrl = "https://gecgithub01.walmart.com/devtools/concord-ansible-example.git";
        String repoBranch = "it";
        String entryPoint = URLEncoder.encode(projectName + ":" + repoName, "UTF-8");

        // ---

        TemplateResource templateResource = proxy(TemplateResource.class);
        templateResource.create(templateName, fsResource(ITConstants.TEMPLATES_DIR + "/ansible-template.zip"));

        // ---

        SecretResource secretResource = proxy(SecretResource.class);
        secretResource.addUsernamePassword(repoSecretName, new UsernamePasswordRequest("username", "password".toCharArray()));

        // ---

        UpdateRepositoryRequest repo = new UpdateRepositoryRequest(repoUrl, repoBranch, repoSecretName);
        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.create(new CreateProjectRequest(projectName, Collections.singleton(templateName), singletonMap(repoName, repo)));

        // ---

        Map<String, InputStream> input = new HashMap<>();
        input.put("request", resource("ansiblegitproject/request.json"));
        input.put("inventory", resource("ansiblegitproject/inventory.ini"));
        StartProcessResponse spr = start(entryPoint, input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessStatusResponse psr = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(psr);
        assertLog(".*Hello, world.*", ab);

        // check if `force_color` is working
        assertLog(".*\\[0;32m.*", 3, ab);
    }

    private static InputStream resource(String path) {
        return AnsibleGitProjectIT.class.getResourceAsStream(path);
    }

    private static InputStream fsResource(String path) throws FileNotFoundException {
        return new FileInputStream(path);
    }
}
