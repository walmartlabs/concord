package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CycleCheckerTest {

    /**
     *
     * m -(m1)- m1 -(mm1)- m2
     *   \(m2)- m2 -(mm2)- m1
     */
    @Test
    public void test1() throws Exception {
        Map<String, Object> m = new HashMap<>();
        Map<String, Object> m1 = new HashMap<>();
        Map<String, Object> m2 = new HashMap<>();

        m1.put("mm1", m2);
        m2.put("mm2", m1);

        m.put("m1", m1);
        m.put("m2", m2);

        System.out.println(CycleChecker.check(m));
        assertTrue(CycleChecker.check(m).isHasCycle());
//        System.out.println(new ObjectMapper().writeValueAsString(m));
    }

    /**
     * m -(m2)- "a1"
     *   \(m2)- m
     */
    @Test
    public void test2() throws Exception {
        Map<String, Object> m = new HashMap<>();

        m.put("m2", Arrays.asList("a1", m));

        System.out.println(CycleChecker.check(m));
        assertTrue(CycleChecker.check(m).isHasCycle());
//        System.out.println(new ObjectMapper().writeValueAsString(m));
    }

    /**
     * m -(k)-- "v"
     *   \(k2)- * -(kk2)- "value"
     */
    @Test
    public void test3() throws Exception {
        Map<String, Object> m = new HashMap<>();

        m.put("k", "v");
        m.put("k2", Collections.singletonMap("kk2", "value"));

        System.out.println(CycleChecker.check(m));
        assertFalse(CycleChecker.check(m).isHasCycle());
        System.out.println(new ObjectMapper().writeValueAsString(m));
    }

    /**
     * m -(m2)- "a1"
     *   \(m2)- * -(kk2)- "value"
     */
    @Test
    public void test4() throws Exception {
        Map<String, Object> m = new HashMap<>();

        m.put("m2", Arrays.asList("a1", Collections.singletonMap("kk2", "value")));

        System.out.println(new ObjectMapper().writeValueAsString(m));
        System.out.println(CycleChecker.check(m));

        assertFalse(CycleChecker.check(m).isHasCycle());
    }

    /**
     * m -(m1)- m1 -(k1)- "v1"
     *   \(m2)- m2 -(k2)- "v2"
     *   \(m2)- m2 -(k3)- m1
     */
    @Test
    public void test5() throws Exception {
        Map<String, Object> m = new HashMap<>();
        Map<String, Object> m1 = new HashMap<>();
        Map<String, Object> m2 = new HashMap<>();

        m1.put("k1", "v1");
        m2.put("k2", "v2");
        m2.put("k3", m1);

        m.put("m1", m1);
        m.put("m2", m2);

        System.out.println(new ObjectMapper().writeValueAsString(m));

        System.out.println(CycleChecker.check(m));
        assertFalse(CycleChecker.check(m).isHasCycle());
    }
}
