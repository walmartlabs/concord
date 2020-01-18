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

public class JsonStoreRule implements Serializable {

    private final StoreRule store;
    private final StoreDataRule data;

    @JsonCreator
    public JsonStoreRule(@JsonProperty("store") StoreRule store,
                         @JsonProperty("data") StoreDataRule data) {

        this.store = store;
        this.data = data;
    }

    public StoreRule getStore() {
        return store;
    }

    public StoreDataRule getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonStoreRule that = (JsonStoreRule) o;
        return Objects.equals(store, that.store) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(store, data);
    }

    public static class StoreRule implements Serializable {

        private final int maxNumberPerOrg;
        private final String msg;

        @JsonCreator
        public StoreRule(@JsonProperty("maxNumberPerOrg") int maxNumberPerOrg,
                         @JsonProperty("msg") String msg) {

            this.maxNumberPerOrg = maxNumberPerOrg;
            this.msg = msg;
        }

        public int getMaxNumberPerOrg() {
            return maxNumberPerOrg;
        }

        public String getMsg() {
            return msg;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StoreRule that = (StoreRule) o;
            return maxNumberPerOrg == that.maxNumberPerOrg &&
                    Objects.equals(msg, that.msg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxNumberPerOrg, msg);
        }
    }

    public static class StoreDataRule implements Serializable {

        private final Long maxSizeInBytes;
        private final String msg;

        @JsonCreator
        public StoreDataRule(@JsonProperty("maxSizeInBytes") Long maxSizeInBytes,
                             @JsonProperty("msg") String msg) {

            this.maxSizeInBytes = maxSizeInBytes;
            this.msg = msg;
        }

        public Long getMaxSizeInBytes() {
            return maxSizeInBytes;
        }

        public String getMsg() {
            return msg;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StoreDataRule that = (StoreDataRule) o;
            return Objects.equals(maxSizeInBytes, that.maxSizeInBytes) &&
                    Objects.equals(msg, that.msg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxSizeInBytes, msg);
        }
    }
}
