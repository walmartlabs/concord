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
import java.util.Map;
import java.util.Objects;

public class EntityRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String msg;

    private final String entity;

    private final String action;

    private final Map<String, Object> conditions;

    @JsonCreator
    public EntityRule(
            @JsonProperty("msg") String msg,
            @JsonProperty("entity") String entity,
            @JsonProperty("action") String action,
            @JsonProperty("conditions") Map<String, Object> conditions) {

        this.msg = msg;
        this.entity = entity;
        this.action = action;
        this.conditions = conditions;
    }

    public String getMsg() {
        return msg;
    }

    public String getEntity() {
        return entity;
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getConditions() {
        return conditions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityRule that = (EntityRule) o;
        return Objects.equals(msg, that.msg) &&
                Objects.equals(entity, that.entity) &&
                Objects.equals(action, that.action) &&
                Objects.equals(conditions, that.conditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, entity, action, conditions);
    }

    @Override
    public String toString() {
        return "EntityRule{" +
                "msg='" + msg + '\'' +
                ", entity='" + entity + '\'' +
                ", action='" + action + '\'' +
                ", conditions=" + conditions +
                '}';
    }
}
