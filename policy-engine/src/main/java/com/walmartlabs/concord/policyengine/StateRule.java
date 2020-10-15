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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class StateRule implements Serializable {

    private final String msg;
    private final Long maxSizeInBytes;
    private final Integer maxFilesCount;
    private final List<String> patterns;

    @JsonCreator
    public StateRule(
            @JsonProperty("msg") String msg,
            @JsonProperty("maxSizeInBytes") Long maxSizeInBytes,
            @JsonProperty("maxFilesCount") Integer maxFilesCount,
            @JsonProperty("patterns") List<String> patterns) {

        this.msg = msg;
        this.maxSizeInBytes = maxSizeInBytes;
        this.maxFilesCount = maxFilesCount;
        this.patterns = Optional.ofNullable(patterns).orElse(Collections.emptyList());
    }

    public String getMsg() {
        return msg;
    }

    public Long getMaxSizeInBytes() {
        return maxSizeInBytes;
    }

    public Integer getMaxFilesCount() {
        return maxFilesCount;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateRule stateRule = (StateRule) o;
        return Objects.equals(msg, stateRule.msg) &&
                Objects.equals(maxSizeInBytes, stateRule.maxSizeInBytes) &&
                Objects.equals(maxFilesCount, stateRule.maxFilesCount) &&
                Objects.equals(patterns, stateRule.patterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, maxSizeInBytes, maxFilesCount, patterns);
    }

    @Override
    public String toString() {
        return "StateRule{" +
                "msg='" + msg + '\'' +
                ", maxSizeInBytes=" + maxSizeInBytes +
                ", maxFilesCount=" + maxFilesCount +
                ", patterns=" + patterns +
                '}';
    }
}
