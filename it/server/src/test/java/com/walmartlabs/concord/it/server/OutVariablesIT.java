package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.Assert.*;

public class OutVariablesIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("out").toURI());
        String[] out = {"x", "y.some.boolean", "z"};

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, true, out);

        Map<String, Object> data = spr.getOut();
        assertNotNull(data);

        assertEquals(123, data.get("x"));
        assertEquals(true, data.get("y.some.boolean"));
        assertFalse(data.containsKey("z"));
    }

    @Test(timeout = 30000)
    public void testPredefined() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("out").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("request", Collections.singletonMap("activeProfiles", Arrays.asList("predefinedOut")));

        StartProcessResponse spr = start(input, true);

        Map<String, Object> data = spr.getOut();
        assertNotNull(data);

        assertEquals(123, data.get("x"));
    }
}
