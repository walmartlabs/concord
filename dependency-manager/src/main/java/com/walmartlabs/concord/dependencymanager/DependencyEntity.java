package com.walmartlabs.concord.dependencymanager;

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

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public class DependencyEntity {

    private final Path path;
    private final Artifact artifact;
    private final URI directLink;

    public DependencyEntity(Path path, String groupId, String artifactId, String version) {
        this.path = path;
        this.artifact = new Artifact(groupId, artifactId, version);
        this.directLink = null;
    }

    public DependencyEntity(Path path, URI directLink) {
        this.path = path;
        this.artifact = null;
        this.directLink = directLink;
    }

    public Path getPath() {
        return path;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public URI getDirectLink() {
        return directLink;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyEntity that = (DependencyEntity) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public final String toString() {
        if (artifact != null) {
            return artifact.toString();
        } else {
            return directLink.toString();
        }
    }

    public static class Artifact {

        private final String groupId;
        private final String artifactId;
        private final String version;

        public Artifact(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public final String toString() {
            return "Artifact{" +
                    "groupId='" + groupId + '\'' +
                    ", artifactId='" + artifactId + '\'' +
                    ", version='" + version + '\'' +
                    '}';
        }
    }
}
