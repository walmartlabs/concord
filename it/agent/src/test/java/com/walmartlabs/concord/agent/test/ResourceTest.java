package com.walmartlabs.concord.agent.test;

import com.walmartlabs.concord.agent.JarIT;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @see JarIT#testNormal()
 */
public class ResourceTest {

    public static void main(String[] args) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ClassLoader.getSystemResourceAsStream("test.txt")))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}
