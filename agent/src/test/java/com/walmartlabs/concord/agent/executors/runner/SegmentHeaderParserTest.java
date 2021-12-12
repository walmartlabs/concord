package com.walmartlabs.concord.agent.executors.runner;

import org.junit.Test;

public class SegmentHeaderParserTest {

    // |msgLength|segmentId|DONE?|warnings|errors|msg

    @Test
    public void test() throws Exception {
        String log = "10|12|1asd|0|0";
    }
}
