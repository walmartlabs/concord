package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.server.process.keys.HeaderKey;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class PayloadTest {

    private static final HeaderKey<String> MY_STRING_HEADER = HeaderKey.register("myStringHeader", String.class);

    @Test
    public void test() throws Exception {
        Payload p = new Payload("a")
                .putHeader(MY_STRING_HEADER, "hello")
                .putHeaders(Collections.singletonMap("a", 1));

        assertEquals("hello", p.getHeader(MY_STRING_HEADER));
        assertEquals(new Integer(1), p.getHeader("a"));
    }
}
