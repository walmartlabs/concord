package com.walmartlabs.concord.agent.test;

import com.walmartlabs.concord.agent.JarIT;

/**
 * @see JarIT#testInterrupted()
 */
public class LongRunningTest {

    public static void main(String[] args) throws Exception {
        System.out.println("working...");
        for (int i = 0; i < 150; i++) {
            System.out.println(i);
            Thread.sleep(100);
        }
    }
}
