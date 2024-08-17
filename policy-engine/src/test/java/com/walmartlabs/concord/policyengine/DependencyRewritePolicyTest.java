package com.walmartlabs.concord.policyengine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DependencyRewritePolicyTest {

    @Test
    public void testRewriteWithMultiValues() throws Exception {
        DependencyRewriteRule r1 = DependencyRewriteRule.builder()
                .msg("msg")
                .groupId("com.walmartlabs.concord.plugins")
                .artifactId("git")
                .values(List.of(
                        new URI("mvn://com.walmartlabs.concord.plugins:git:2.3.1"),
                        new URI("mvn://com.google.code.gson:gson:2.8.7")))
                .build();

        DependencyRewritePolicy policy = new DependencyRewritePolicy(List.of(r1));

        List<URI> dependencies = List.of(new URI("mvn://com.walmartlabs.concord.plugins:git:1.0.1"));

        List<URI> result = (List<URI>) policy.rewrite(dependencies, (msg, from, to) -> {});

        assertEquals(2, result.size());
        assertEquals(new URI("mvn://com.walmartlabs.concord.plugins:git:2.3.1"), result.get(0));
        assertEquals(new URI("mvn://com.google.code.gson:gson:2.8.7"), result.get(1));
    }

    @Test
    public void testRewriteNotMatch() throws Exception {
        DependencyRewriteRule r1 = DependencyRewriteRule.builder()
                .msg("msg")
                .groupId("com.walmartlabs.concord.plugins")
                .artifactId("git")
                .values(List.of(
                        new URI("mvn://com.walmartlabs.concord.plugins:git:2.3.1"),
                        new URI("mvn://com.google.code.gson:gson:2.8.7")))
                .build();

        DependencyRewritePolicy policy = new DependencyRewritePolicy(List.of(r1));

        List<URI> dependencies = List.of(new URI("mvn://my.plugin:my:1.0.1"));

        List<URI> result = (List<URI>) policy.rewrite(dependencies, (msg, from, to) -> {});
        assertEquals(1, result.size());
        assertEquals(dependencies.get(0), result.get(0));
    }

}
