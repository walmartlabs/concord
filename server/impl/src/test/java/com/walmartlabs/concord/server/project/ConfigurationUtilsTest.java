package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ConfigurationUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConfigurationUtilsTest {

    @Test
    @SuppressWarnings("unchecked")
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

    @Test
    @SuppressWarnings("unchecked")
    public void testDeepMerge() throws Exception {
        ObjectMapper om = new ObjectMapper();

        String cfgJson = "{ \"arguments\": { \"smtpParams\": { \"host\": \"localhost\" } } }";
        Map<String, Object> cfg = om.readValue(cfgJson, Map.class);

        String requestJson = "{ \"arguments\": { \"mail\": \"hello\" } }";
        Map<String, Object> request = om.readValue(requestJson, Map.class);

        // ---

        Map<String, Object> m = ConfigurationUtils.deepMerge(cfg, request);
        assertEquals("localhost", ConfigurationUtils.get(m, "arguments", "smtpParams", "host"));
        assertEquals("hello", ConfigurationUtils.get(m, "arguments", "mail"));
    }

    @Test
    public void testMerge() throws Exception {
        Map<String, Object> a = new HashMap<>();
        a.put("x", 123);

        Map<String, Object> b = new HashMap<>();
        b.put("y", 234);

        ConfigurationUtils.merge(a, b);
        assertEquals(234, a.get("y"));
    }
}
