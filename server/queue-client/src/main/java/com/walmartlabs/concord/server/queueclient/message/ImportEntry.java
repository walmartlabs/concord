package com.walmartlabs.concord.server.queueclient.message;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

import static com.walmartlabs.concord.project.model.Import.SecretDefinition;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @Type(value = ImportEntry.GitEntry.class, name = "git"),
        @Type(value = ImportEntry.MvnEntry.class, name = "mvn"),
        @Type(value = ImmutableGitEntry.class, name = "git"),
        @Type(value = ImmutableMvnEntry.class, name = "mvn"),
})
public interface ImportEntry {

    String type();

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableGitEntry.class)
    @JsonDeserialize(as = ImmutableGitEntry.class)
    interface GitEntry extends ImportEntry {

        String url();

        String version();

        @Nullable
        String path();

        String dest();

        @Nullable
        SecretDefinition secret();

        @Override
        default String type() {
            return "git";
        }

        static ImmutableGitEntry.Builder builder() {
            return ImmutableGitEntry.builder();
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableMvnEntry.class)
    @JsonDeserialize(as = ImmutableMvnEntry.class)
    interface MvnEntry extends ImportEntry {

        String url();

        @Nullable
        String dest();

        @Override
        default String type() {
            return "mvn";
        }

        static ImmutableMvnEntry.Builder builder() {
            return ImmutableMvnEntry.builder();
        }
    }
}
