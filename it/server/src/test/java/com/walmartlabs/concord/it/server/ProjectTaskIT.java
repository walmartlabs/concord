package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class ProjectTaskIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testCreate() throws Exception {
        String teamName = "Default";

        // ---

        String projectName = "project_" + System.currentTimeMillis();
        String repoName = "repo_" + System.currentTimeMillis();
        String repoUrl = "git://127.0.0.1/test.git";
        String repoSecret = "secret_" + System.currentTimeMillis();
        addUsernamePassword(teamName, repoSecret, false, null, "user_" + System.currentTimeMillis(), "pwd_" + System.currentTimeMillis());

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("projectTask").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        Map<String, Object> args = new HashMap<>();
        args.put("projectName", projectName);
        args.put("repoName", repoName);
        args.put("repoUrl", repoUrl);
        args.put("repoSecret", repoSecret);

        input.put("request", Collections.singletonMap("arguments", args));

        // ---

        StartProcessResponse spr = start(input);

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*CREATED.*", ab);
        assertLog(".*Done!.*", ab);
    }
}
