package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.events.GithubEventResource;
import org.junit.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GithubCallbackIT extends AbstractServerIT {

    // for empty event and '123qwe' secret
    private static final String AUTH = "sha1=047cfb383db684bdfccc2c333698b70ee98e65d2";

    private static final UUID concordTriggersProject = UUID.fromString("ad76f1e2-c33c-11e7-8064-f7371c66fa77");
    private static final UUID concordTriggersRepoId = UUID.fromString("b31b0b06-c33c-11e7-b0e9-8702fc03629f");

    @Test(timeout = 30000)
    public void test() throws Exception {
        setGithubKey(AUTH);

        GithubEventResource githubResource = proxy(GithubEventResource.class);
        String result = githubResource.push(concordTriggersProject, concordTriggersRepoId, new HashMap<>());
        assertNotNull(result);
        assertEquals("ok", result);
    }
}
