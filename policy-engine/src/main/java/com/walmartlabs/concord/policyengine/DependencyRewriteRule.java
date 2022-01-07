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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

public class DependencyRewriteRule implements Serializable {

    private static final long serialVersionUID = -1L;

    private final String msg;
    private final String groupId;
    private final String artifactId;
    private final String fromVersion;
    private final String toVersion;
    private final URI value;

    @JsonCreator
    public DependencyRewriteRule(
            @JsonProperty("msg") String msg,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("fromVersion") String fromVersion,
            @JsonProperty("toVersion") String toVersion,
            @JsonProperty("value") URI value) {

        this.msg = msg;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.value = value;
    }

    public String getMsg() {
        return msg;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getFromVersion() {
        return fromVersion;
    }

    public String getToVersion() {
        return toVersion;
    }

    public URI getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyRewriteRule that = (DependencyRewriteRule) o;
        return Objects.equals(msg, that.msg) && Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(fromVersion, that.fromVersion) && Objects.equals(toVersion, that.toVersion) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, groupId, artifactId, fromVersion, toVersion, value);
    }

    @Override
    public String toString() {
        return "DependencyRewriteRule{" +
                "msg='" + msg + '\'' +
                ", groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", fromVersion='" + fromVersion + '\'' +
                ", toVersion='" + toVersion + '\'' +
                ", value=" + value +
                '}';
    }
}
