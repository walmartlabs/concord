package com.walmartlabs.concord.it.agent.nexusperf.scenarios.test01

import org.junit.After
import org.junit.Before
import org.junit.Test

class Test01 {

    @Before
    void setUp() {
        System.out.println("Test01: before")
    }

    @After
    void tearDown() {
        System.out.println("Test01: after")
    }

    @Test
    public void test() {
        System.out.println("Test01: test")
    }
}
