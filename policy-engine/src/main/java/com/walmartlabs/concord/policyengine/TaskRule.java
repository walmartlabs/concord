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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.common.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableTaskRule.class)
@JsonDeserialize(as = ImmutableTaskRule.class)
public interface TaskRule extends Serializable {

    long serialVersionUID = 1L;

    @Nullable
    String msg();

    @Nullable
    @JsonProperty("name")
    @JsonAlias("taskName")
    String taskName();

    @Nullable
    String method();

    @Value.Default
    default List<Param> params() {
        return Collections.emptyList();
    }

    @Value.Default
    default List<TaskResult> taskResults() {
        return Collections.emptyList();
    }

    static ImmutableTaskRule.Builder builder() {
        return ImmutableTaskRule.builder();
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableParam.class)
    @JsonDeserialize(as = ImmutableParam.class)
    interface Param extends Serializable {

        long serialVersionUID = 1L;

        int index();

        @Nullable
        String name();

        @JsonProperty("protected")
        boolean protectedVariable();

        @Value.Default
        @AllowNulls
        default List<Object> values() {
            return Collections.emptyList();
        }

        static ImmutableParam.Builder builder() {
            return ImmutableParam.builder();
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableTaskResult.class)
    @JsonDeserialize(as = ImmutableTaskResult.class)
    interface TaskResult extends Serializable {

        long serialVersionUID = 1L;

        String task();

        @Nullable
        String result();

        @Value.Default
        @AllowNulls
        default List<Object> values() {
            return Collections.emptyList();
        }

        static ImmutableTaskResult.Builder builder() {
            return ImmutableTaskResult.builder();
        }
    }
}
