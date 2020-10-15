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

public class ForkDepthRule implements Serializable {

    private final String msg;
    private final int max;

    @JsonCreator
    public ForkDepthRule(@JsonProperty("msg") String msg,
                         @JsonProperty("max") int max) {
        this.msg = msg;
        this.max = max;
    }

    public String getMsg() {
        return msg;
    }

    public int getMax() {
        return max;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForkDepthRule that = (ForkDepthRule) o;
        return max == that.max &&
                Objects.equals(msg, that.msg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, max);
    }

    @Override
    public String toString() {
        return "ForkDepthRule{" +
                "msg='" + msg + '\'' +
                ", max=" + max +
                '}';
    }
}
