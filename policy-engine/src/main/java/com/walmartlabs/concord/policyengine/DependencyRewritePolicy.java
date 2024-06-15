package com.walmartlabs.concord.policyengine;

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

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.walmartlabs.concord.dependencymanager.DependencyManager.MAVEN_SCHEME;
import static com.walmartlabs.concord.policyengine.Utils.matches;

public class DependencyRewritePolicy {

    private final List<DependencyRewriteRule> rules;

    public DependencyRewritePolicy(List<DependencyRewriteRule> rules) {
        this.rules = rules;
    }

    public Collection<URI> rewrite(Collection<URI> dependencies, RewriteListener listener) {
        if (rules == null || rules.isEmpty() || dependencies.isEmpty()) {
            return dependencies;
        }

        List<URI> result = new ArrayList<>(dependencies.size());
        for (URI u : dependencies) {
            result.add(rewrite(u, listener));
        }
        return result;
    }

    private URI rewrite(URI value, RewriteListener listener) {
        String scheme = value.getScheme();
        if (!MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
            return value;
        }

        Artifact artifact = new DefaultArtifact(value.getAuthority());
        for (DependencyRewriteRule rule : rules) {
            if (match(rule, artifact)) {
                listener.onRewrite(rule.msg(), value, rule.value());
                return rule.value();
            }
        }

        return value;
    }

    private static boolean match(DependencyRewriteRule r, Artifact a) {
        if (r.groupId() != null && !matches(r.groupId(), a.getGroupId())) {
            return false;
        }

        if (r.artifactId() != null && !matches(r.artifactId(), a.getArtifactId())) {
            return false;
        }

        if (r.fromVersion() != null && compareVersions(r.fromVersion(), a.getVersion()) > 0) {
            return false;
        }

        if (r.toVersion() != null && compareVersions(r.toVersion(), a.getVersion()) < 0) {
            return false;
        }

        return true;
    }

    private static int compareVersions(String a, String b) {
        ComparableVersion v1 = new ComparableVersion(a);
        ComparableVersion v2 = new ComparableVersion(b);
        return v1.compareTo(v2);
    }

    public interface RewriteListener {

        void onRewrite(String msg, URI from, URI to);
    }
}
