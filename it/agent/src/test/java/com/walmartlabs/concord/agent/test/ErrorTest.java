package com.walmartlabs.concord.agent.test;

import com.walmartlabs.concord.agent.JarIT;

/**
 * @see JarIT#testError()
 */
public class ErrorTest {

    public static void main(String[] args) throws Exception {
        throw new Exception("Kaboom!");
    }
}
