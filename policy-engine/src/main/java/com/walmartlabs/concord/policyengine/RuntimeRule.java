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

public class RuntimeRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String msg;
    private final Set<String> allowedRuntimes;

    @JsonCreator
    public RuntimeRule(@JsonProperty("msg") String msg,
                       @JsonProperty("runtimes") Set<String> allowedRuntimes) {

        this.msg = msg;
        this.allowedRuntimes = allowedRuntimes;
    }

    public String getMsg() {
        return msg;
    }

    @JsonProperty("runtimes")
    public Set<String> getAllowedRuntimes() {
        return allowedRuntimes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuntimeRule that = (RuntimeRule) o;
        return Objects.equals(msg, that.msg) && Objects.equals(allowedRuntimes, that.allowedRuntimes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, allowedRuntimes);
    }

    @Override
    public String toString() {
        return "RuntimeRule{" +
                "msg='" + msg + '\'' +
                ", allowedRuntimes=" + allowedRuntimes +
                '}';
    }
}
