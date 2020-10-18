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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class FileRule implements Serializable {

    private final String msg;

    private final Long maxSizeInBytes;
    private final String maxSize;
    private final Type type;
    private final List<String> names;

    @JsonCreator
    public FileRule(
            @JsonProperty("msg") String msg,
            @JsonProperty("maxSize") String maxSize,
            @JsonProperty("type") String type,
            @JsonProperty("names") List<String> names) {

        this.msg = msg;
        this.maxSizeInBytes = Utils.parseFileSize(maxSize);
        this.maxSize = maxSize;
        this.type = Optional.ofNullable(type).map(v -> Type.valueOf(v.toUpperCase())).orElse(Type.FILE);
        this.names = Optional.ofNullable(names).orElse(Collections.emptyList());
    }

    public String getMsg() {
        return msg;
    }

    public String getMaxSize() {
        return maxSize;
    }

    @JsonIgnore
    public Long getMaxSizeInBytes() {
        return maxSizeInBytes;
    }

    public Type getType() {
        return type;
    }

    public List<String> getNames() {
        return names;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof FileRule)) return false;
        FileRule fileRule = (FileRule) o;
        return Objects.equals(msg, fileRule.msg) &&
                Objects.equals(maxSizeInBytes, fileRule.maxSizeInBytes) &&
                Objects.equals(maxSize, fileRule.maxSize) &&
                type == fileRule.type &&
                Objects.equals(names, fileRule.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, maxSizeInBytes, maxSize, type, names);
    }

    @Override
    public String toString() {
        return "FileRule{" +
                "msg='" + msg + '\'' +
                ", maxSizeInBytes=" + maxSizeInBytes +
                ", maxSize='" + maxSize + '\'' +
                ", type=" + type +
                ", names=" + names +
                '}';
    }

    public enum Type {
        FILE, DIR
    }
}
