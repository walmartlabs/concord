package com.walmartlabs.concord.server.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class SerializationTest {

    @Test
    public void testRoundtrip() throws Exception {
        String src = "{\"username\":\"myUser\",\"permissions\":[\"project:myProject\",\"process:*:myProject\"]}";

        // ---

        ObjectMapper om = new ObjectMapper();
        CreateUserRequest req = om.readValue(src, CreateUserRequest.class);

        assertEquals("myUser", req.getUsername());

        // ---

        Set<String> s = req.getPermissions();
        assertNotNull(s);
        assertEquals(2, s.size());

        assertTrue(s.contains("project:myProject"));
        assertTrue(s.contains("process:*:myProject"));

        // ---

        om.writeValueAsString(req);
    }
}
