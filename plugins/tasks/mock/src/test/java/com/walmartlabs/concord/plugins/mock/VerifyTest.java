package com.walmartlabs.concord.plugins.mock;

import com.walmartlabs.concord.runtime.v2.runner.DefaultPersistenceService;
import com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class VerifyTest {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2()
            .withPersistenceService(DefaultPersistenceService.class);

    @Test
    public void testVerify() throws Exception {
        runtime.deploy("simple-verify");

        runtime.run();
    }

    @Test
    public void testVerifyMockedTask() throws Exception {
        runtime.deploy("verify-mocked-task");

        runtime.run();
    }
}
