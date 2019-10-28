package com.walmartlabs.concord.db;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PgIntRangeTest {

    @Test
    public void test() throws Exception {
        String s = "[123,234)";
        PgIntRange r = PgIntRange.parse(s);
        assertEquals(PgIntRange.Mode.INCLUSIVE, r.getLowerMode());
        assertEquals(123, r.getLower());
        assertEquals(234, r.getUpper());
        assertEquals(PgIntRange.Mode.EXCLUSIVE, r.getUpperMode());
    }
}
