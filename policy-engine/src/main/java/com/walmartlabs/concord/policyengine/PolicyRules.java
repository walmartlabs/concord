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
import java.util.Optional;

public class PolicyRules<E> implements Serializable {

    private final List<E> allow;
    private final List<E> warn;
    private final List<E> deny;

    @JsonCreator
    public PolicyRules(
            @JsonProperty("allow") List<E> allow,
            @JsonProperty("warn") List<E> warn,
            @JsonProperty("deny") List<E> deny) {
        this.allow = Optional.ofNullable(allow).orElse(Collections.emptyList());
        this.warn = Optional.ofNullable(warn).orElse(Collections.emptyList());
        this.deny = Optional.ofNullable(deny).orElse(Collections.emptyList());
    }

    public List<E> getAllow() {
        return allow;
    }

    public List<E> getWarn() {
        return warn;
    }

    public List<E> getDeny() {
        return deny;
    }

    public boolean isEmpty() {
        return allow.isEmpty() && deny.isEmpty() && warn.isEmpty();
    }

    @Override
    public String toString() {
        return "{" +
                "allow=" + allow +
                ", warn=" + warn +
                ", deny=" + deny +
                '}';
    }
}
