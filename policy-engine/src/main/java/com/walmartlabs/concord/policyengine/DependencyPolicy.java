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

import com.walmartlabs.concord.dependencymanager.DependencyEntity;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.walmartlabs.concord.policyengine.Utils.matches;

public class DependencyPolicy {

    private final PolicyRules<DependencyRule> rules;

    public DependencyPolicy(PolicyRules<DependencyRule> rules) {
        this.rules = rules;
    }

    public CheckResult<DependencyRule, DependencyEntity> check(Collection<DependencyEntity> dependencies) {
        if (rules == null || rules.isEmpty()) {
            return CheckResult.success();
        }

        List<CheckResult.Item<DependencyRule, DependencyEntity>> warn = new ArrayList<>();
        List<CheckResult.Item<DependencyRule, DependencyEntity>> deny = new ArrayList<>();

        for (DependencyEntity e : dependencies) {
            check(e, warn, deny);
        }

        return new CheckResult<>(warn, deny);
    }

    private void check(DependencyEntity d,
                       List<CheckResult.Item<DependencyRule, DependencyEntity>> warn,
                       List<CheckResult.Item<DependencyRule, DependencyEntity>> deny) {

        for (DependencyRule r : rules.getAllow()) {
            if (matchRule(r, d)) {
                return;
            }
        }

        for (DependencyRule r : rules.getDeny()) {
            if (matchRule(r, d)) {
                deny.add(new CheckResult.Item<>(r, d));
                return;
            }
        }

        for (DependencyRule r : rules.getWarn()) {
            if (matchRule(r, d)) {
                warn.add(new CheckResult.Item<>(r, d));
                return;
            }
        }
    }

    private static boolean matchRule(DependencyRule r, DependencyEntity d) {
        if (d.getArtifact() != null) {
            return matchRule(r, d.getArtifact());
        } else {
            return matchRule(r, d.getDirectLink());
        }
    }

    private static boolean matchRule(DependencyRule r, DependencyEntity.Artifact a) {
        if (r.scheme() != null && !matches(r.scheme(), "mvn")) {
            return false;
        }

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

    private static boolean matchRule(DependencyRule r, URI directLink) {
        if (r.scheme() != null && matches(r.scheme(), directLink.getScheme())) {
            return true;
        }

        return false;
    }

    private static int compareVersions(String a, String b) {
        ComparableVersion v1 = new ComparableVersion(a);
        ComparableVersion v2 = new ComparableVersion(b);
        return v1.compareTo(v2);
    }

}
