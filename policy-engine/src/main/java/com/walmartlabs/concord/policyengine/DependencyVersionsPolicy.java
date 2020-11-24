package com.walmartlabs.concord.policyengine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DependencyVersionsPolicy {

    private final List<Dependency> rules;

    public DependencyVersionsPolicy(List<Dependency> rules) {
        this.rules = rules != null ? rules : Collections.emptyList();
    }

    public List<Dependency> get() {
        return rules;
    }

    public static class Dependency {

        private final String artifact;
        private final String version;

        public Dependency(@JsonProperty("artifact") String artifact,
                          @JsonProperty("version") String version) {

            this.artifact = artifact;
            this.version = version;
        }

        public String getArtifact() {
            return artifact;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || !(o instanceof Dependency)) return false;
            Dependency that = (Dependency) o;
            return Objects.equals(artifact, that.artifact) &&
                    Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifact, version);
        }
    }
}
