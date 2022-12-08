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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableJsonStoreRule.class)
@JsonDeserialize(as = ImmutableJsonStoreRule.class)
public interface JsonStoreRule extends Serializable {

    long serialVersionUID = 1L;

    @Nullable
    @Value.Parameter
    StoreRule store();

    @Nullable
    @Value.Parameter
    StoreDataRule data();

    static JsonStoreRule of(StoreRule store, StoreDataRule data) {
        return ImmutableJsonStoreRule.of(store, data);
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableStoreRule.class)
    @JsonDeserialize(as = ImmutableStoreRule.class)
    interface StoreRule extends Serializable {

        long serialVersionUID = 1L;

        @Value.Parameter
        @Nullable
        String msg();

        @Value.Parameter
        int maxNumberPerOrg();

        static StoreRule of(String msg, int maxNumberPerOrg) {
            return ImmutableStoreRule.of(msg, maxNumberPerOrg);
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableStoreDataRule.class)
    @JsonDeserialize(as = ImmutableStoreDataRule.class)
    interface StoreDataRule extends Serializable {

        long serialVersionUID = 1L;

        @Nullable
        @Value.Parameter
        String msg();

        @Value.Parameter
        long maxSizeInBytes();

        static StoreDataRule of(String msg, long maxSizeInBytes) {
            return ImmutableStoreDataRule.of(msg, maxSizeInBytes);
        }
    }
}
