package com.walmartlabs.concord.imports;

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
import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @Type(value = Import.GitDefinition.class, name = Import.GitDefinition.TYPE),
        @Type(value = ImmutableGitDefinition.class, name = Import.GitDefinition.TYPE),
        @Type(value = Import.MvnDefinition.class, name = Import.MvnDefinition.TYPE),
        @Type(value = ImmutableMvnDefinition.class, name = Import.MvnDefinition.TYPE),
})
public interface Import extends Serializable {

    long serialVersionUID = 1L;

    String type();

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableGitDefinition.class)
    @JsonDeserialize(as = ImmutableGitDefinition.class)
    interface GitDefinition extends Import {

        String TYPE = "git";

        @Nullable
        String name();

        @Nullable
        String url();

        @Nullable
        String version();

        @Nullable
        String path();

        @Nullable
        String dest();

        @Nullable
        SecretDefinition secret();

        @Override
        default String type() {
            return "git";
        }

        static ImmutableGitDefinition.Builder builder() {
            return ImmutableGitDefinition.builder();
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableMvnDefinition.class)
    @JsonDeserialize(as = ImmutableMvnDefinition.class)
    interface MvnDefinition extends Import {

        String TYPE = "mvn";

        String url();

        @Nullable
        String dest();

        @Override
        default String type() {
            return "mvn";
        }

        static ImmutableMvnDefinition.Builder builder() {
            return ImmutableMvnDefinition.builder();
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableSecretDefinition.class)
    @JsonDeserialize(as = ImmutableSecretDefinition.class)
    interface SecretDefinition extends Serializable {

        @Nullable
        String org();

        String name();

        @Nullable
        @Value.Redacted
        String password();

        static ImmutableSecretDefinition.Builder builder() {
            return ImmutableSecretDefinition.builder();
        }
    }
}
