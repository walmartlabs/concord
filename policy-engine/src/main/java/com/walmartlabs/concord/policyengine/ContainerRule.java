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

public class ContainerRule implements Serializable {

    private final String msg;
    private final String maxRam;
    private final Integer maxCpu;

    @JsonCreator
    public ContainerRule(@JsonProperty("msg") String msg,
                         @JsonProperty("maxRam") String maxRam,
                         @JsonProperty("maxCpu") Integer maxCpu) {

        this.msg = msg;
        this.maxRam = maxRam;
        this.maxCpu = maxCpu;
    }

    public String getMsg() {
        return msg;
    }

    public String getMaxRam() {
        return maxRam;
    }

    public Integer getMaxCpu() {
        return maxCpu;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContainerRule)) return false;
        ContainerRule that = (ContainerRule) o;
        return Objects.equals(msg, that.msg) &&
                Objects.equals(maxRam, that.maxRam) &&
                Objects.equals(maxCpu, that.maxCpu);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, maxRam, maxCpu);
    }

    @Override
    public String toString() {
        return "ContainerRule{" +
                "msg='" + msg + '\'' +
                ", maxRam='" + maxRam + '\'' +
                ", maxCpu=" + maxCpu +
                '}';
    }
}
