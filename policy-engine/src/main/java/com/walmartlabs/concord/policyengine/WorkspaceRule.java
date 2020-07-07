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
import java.util.Objects;
import java.util.Set;

public class WorkspaceRule implements Serializable {

    private final String msg;
    private final Long maxSizeInBytes;
    private final Set<String> ignoredFiles;

    @JsonCreator
    public WorkspaceRule(@JsonProperty("msg") String msg,
                         @JsonProperty("maxSizeInBytes") Long maxSizeInBytes,
                         @JsonProperty("ignoredFiles") Set<String> ignoredFiles) {

        this.msg = msg;
        this.maxSizeInBytes = maxSizeInBytes;
        this.ignoredFiles = ignoredFiles;
    }

    public String getMsg() {
        return msg;
    }

    public Long getMaxSizeInBytes() {
        return maxSizeInBytes;
    }

    public Set<String> getIgnoredFiles() {
        return ignoredFiles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkspaceRule that = (WorkspaceRule) o;
        return Objects.equals(msg, that.msg) &&
                Objects.equals(maxSizeInBytes, that.maxSizeInBytes) &&
                Objects.equals(ignoredFiles, that.ignoredFiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, maxSizeInBytes, ignoredFiles);
    }

    @Override
    public String toString() {
        return "WorkspaceRule{" +
                "msg='" + msg + '\'' +
                ", maxSizeInBytes=" + maxSizeInBytes +
                ", ignoredFiles=" + ignoredFiles +
                '}';
    }
}
