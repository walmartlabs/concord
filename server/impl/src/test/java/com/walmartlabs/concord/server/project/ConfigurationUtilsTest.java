package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.project.ConfigurationUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConfigurationUtilsTest {

    @Test
    public void test() throws Exception {
        String json = "{ \"smtp\": { \"host\": \"localhost\" }, \"ssl\": false }";

        ObjectMapper om = new ObjectMapper();
        Map<String, Object> m = om.readValue(json, Map.class);

        assertEquals("localhost", ConfigurationUtils.get(m, "smtp", "host"));
        assertEquals(false, ConfigurationUtils.get(m, "ssl"));

        // ---

        Map<String, Object> n = new HashMap<>();
        n.put("host", "127.0.0.1");

        ConfigurationUtils.merge(m, n, "smtp");

        assertEquals("127.0.0.1", ConfigurationUtils.get(m, "smtp", "host"));

        // ---

        String newJson = "{ \"smtp\": { \"host\": \"10.11.12.13\" } }";
        Map<String, Object> nn = om.readValue(newJson, Map.class);

        ConfigurationUtils.merge(m, nn);

        assertEquals("10.11.12.13", ConfigurationUtils.get(m, "smtp", "host"));
        assertEquals(false, ConfigurationUtils.get(m, "ssl"));
    }
}
