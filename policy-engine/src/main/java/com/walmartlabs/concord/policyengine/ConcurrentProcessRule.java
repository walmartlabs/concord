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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

public class ConcurrentProcessRule implements Serializable {

    private final String msg;
    private final Integer maxPerOrg;
    private final Integer maxPerProject;

    @JsonCreator
    public ConcurrentProcessRule(@JsonProperty("msg") String msg,
                                 @JsonProperty("maxPerOrg") Integer maxPerOrg,
                                 @JsonProperty("maxPerProject") @JsonAlias("max") Integer maxPerProject) { // "max" for backward compatibility with existing policies

        this.msg = msg;
        this.maxPerOrg = maxPerOrg;
        this.maxPerProject = maxPerProject;
    }

    public String getMsg() {
        return msg;
    }

    public Integer getMaxPerOrg() {
        return maxPerOrg;
    }

    public Integer getMaxPerProject() {
        return maxPerProject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConcurrentProcessRule that = (ConcurrentProcessRule) o;
        return Objects.equals(msg, that.msg) &&
                Objects.equals(maxPerOrg, that.maxPerOrg) &&
                Objects.equals(maxPerProject, that.maxPerProject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, maxPerOrg, maxPerProject);
    }

    @Override
    public String toString() {
        return "ConcurrentProcessRule{" +
                "msg='" + msg + '\'' +
                ", maxPerOrg=" + maxPerOrg +
                ", maxPerProject=" + maxPerProject +
                '}';
    }
}
