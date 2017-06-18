package com.walmartlabs.concord.common;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TailerTest {

    @Test
    public void test() throws Exception {
        List<String> lines = new ArrayList<>();
        ByteArrayInputStream in = new ByteArrayInputStream("1\n2\n3".getBytes());

        // ---

        Tailer tailer = new Tailer(in) {

            @Override
            protected boolean isDone() {
                return true;
            }

            @Override
            protected void handle(byte[] buf, int len) {
                lines.add(new String(buf, 0, len));
            }
        };
        tailer.run();

        // ---

        assertEquals(2, lines.size());
        assertEquals("1\n2\n", lines.get(0));
        assertEquals("3", lines.get(1));
    }
}
