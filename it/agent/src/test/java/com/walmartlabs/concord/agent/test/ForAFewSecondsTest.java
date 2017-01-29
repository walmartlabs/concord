package com.walmartlabs.concord.agent.test;

import com.walmartlabs.concord.agent.JarIT;

/**
 * @see JarIT#testAsyncLog()
 */
public class ForAFewSecondsTest {

    public static void main(String[] args) throws Exception {
        System.out.println("AAA");
        Thread.sleep(3000);
        System.out.println("BBB");
    }
}
