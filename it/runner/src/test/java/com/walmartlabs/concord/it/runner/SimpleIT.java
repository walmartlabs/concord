package com.walmartlabs.concord.it.runner;

import org.junit.Test;

import java.io.File;
import java.util.UUID;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.Assert.assertEquals;

public class SimpleIT extends AbstractRunnerIT {

    @Test
    public void test() throws Exception {
        String instanceId = UUID.randomUUID().toString();

        Process proc = exec(instanceId, new File(SimpleIT.class.getResource("simple").toURI()));

        byte[] ab = readLog(proc);

        int code = proc.waitFor();
        assertEquals(0, code);

        assertEquals(1, grep(".*Hello, Concord.*", ab).size());
    }
}
