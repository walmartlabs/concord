package com.walmartlabs.concord.runtime.v2.runner.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.Serializable;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResumeEventImplTest {

    @Test
    public void jsonSerializationTest() throws Exception {
        Map<String, Serializable> payload = Map.of("key", "key-value");

        ResumeEventImpl event = new ResumeEventImpl("test", payload);
        String json = new ObjectMapper().writeValueAsString(event);
        assertEquals("{\"eventName\":\"test\",\"state\":{\"key\":\"key-value\"}}", json);
    }
}
