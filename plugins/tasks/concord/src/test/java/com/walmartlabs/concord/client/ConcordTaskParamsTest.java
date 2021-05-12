package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.client.ConcordTaskParams.ForkParams;
import com.walmartlabs.concord.client.ConcordTaskParams.ForkStartParams;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConcordTaskParamsTest {

    @Test
    public void testForks() {
        List<String> tags = Arrays.asList("x", "y", "z");

        Map<String, Object> input = new HashMap<>();
        input.put("action", "fork");
        input.put("tags", tags);
        input.put("forks", Arrays.asList(
                Collections.singletonMap("entryPoint", "aaa"),
                Collections.singletonMap("entryPoint", "bbb")
        ));

        ForkParams params = (ForkParams) ConcordTaskParams.of(new MapBackedVariables(input));

        List<ForkStartParams> forks = params.forks();
        assertEquals(2, forks.size());

        ForkStartParams f1 = forks.get(0);
        assertEquals("aaa", f1.entryPoint());
        assertTrue(f1.tags().containsAll(tags));

        ForkStartParams f2 = forks.get(1);
        assertEquals("bbb", f2.entryPoint());
        assertTrue(f2.tags().containsAll(tags));

        System.out.println(params);
    }
}