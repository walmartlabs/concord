package com.walmartlabs.concord.plugins.mock;

import com.walmartlabs.concord.runtime.v2.runner.AbstractTest;
import org.junit.jupiter.api.Test;

public class MainTest extends AbstractTest {

    @Test
    public void test() throws Exception {
        deploy("simple");

        save(cfgFromDeployment().build());

        byte[] log = run();
        assertLog(log, ".*result.ok: true.*");
    }
}
