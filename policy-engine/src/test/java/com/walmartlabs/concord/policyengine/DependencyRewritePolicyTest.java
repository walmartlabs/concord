package com.walmartlabs.concord.policyengine;

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
