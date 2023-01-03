package com.walmartlabs.concord.imports;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.common.ToStringHelper;
import org.immutables.value.Value;
import org.immutables.serial.Serial;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.URL;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @Type(value = Import.GitDefinition.class, name = Import.GitDefinition.TYPE),
        @Type(value = ImmutableGitDefinition.class, name = Import.GitDefinition.TYPE),
        @Type(value = Import.MvnDefinition.class, name = Import.MvnDefinition.TYPE),
        @Type(value = ImmutableMvnDefinition.class, name = Import.MvnDefinition.TYPE),
        @Type(value = Import.DirectoryDefinition.class, name = Import.DirectoryDefinition.TYPE),
        @Type(value = ImmutableDirectoryDefinition.class, name = Import.DirectoryDefinition.TYPE),
})
@Serial.Version(1)
public interface Import extends Serializable {

    String type();

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableGitDefinition.class)
    @JsonDeserialize(as = ImmutableGitDefinition.class)
    abstract class GitDefinition implements Import {

        private static final long serialVersionUID = 1L;

        public static final String TYPE = "git";

        @Nullable
        public abstract String name();

        @Nullable
        public abstract String url();

        @Nullable
        public abstract String version();

        @Nullable
        public abstract String path();

        @Nullable
        public abstract String dest();

        @Nullable
        public abstract SecretDefinition secret();

        @Value.Default
        public List<String> exclude() {
            return Collections.emptyList();
        }

        @Override
        public String type() {
            return TYPE;
        }

        public static ImmutableGitDefinition.Builder builder() {
            return ImmutableGitDefinition.builder();
        }

        public String toString() {
            return ToStringHelper.prefix(TYPE + ": ")
                    .add("name", name())
                    .add("url", hideSensitiveData(url()))
                    .add("version", version())
                    .add("path", path())
                    .add("dest", dest())
                    .add("secret", secret())
                    .add("exclude", exclude())
                    .toString();
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableMvnDefinition.class)
    @JsonDeserialize(as = ImmutableMvnDefinition.class)
    abstract class MvnDefinition implements Import {

        private static final long serialVersionUID = 1L;

        public static final String TYPE = "mvn";

        @JsonProperty(value = "url", required = true)
        public abstract String url();

        @Nullable
        public abstract String dest();

        @Override
        public String type() {
            return TYPE;
        }

        public static ImmutableMvnDefinition.Builder builder() {
            return ImmutableMvnDefinition.builder();
        }

        public String toString() {
            return ToStringHelper.prefix(TYPE + ": ")
                    .add("url", hideSensitiveData(url()))
                    .add("dest", dest())
                    .toString();
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableDirectoryDefinition.class)
    @JsonDeserialize(as = ImmutableDirectoryDefinition.class)
    abstract class DirectoryDefinition implements Import {

        private static final long serialVersionUID = 1L;

        public static final String TYPE = "dir";

        @JsonProperty(value = "src", required = true)
        public abstract String src();

        @Nullable
        public abstract String dest();

        @Override
        public String type() {
            return TYPE;
        }

        public static ImmutableDirectoryDefinition.Builder builder() {
            return ImmutableDirectoryDefinition.builder();
        }

        public String toString() {
            return ToStringHelper.prefix(TYPE + ": ")
                    .add("src", src())
                    .add("dest", dest())
                    .toString();
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableSecretDefinition.class)
    @JsonDeserialize(as = ImmutableSecretDefinition.class)
    abstract class SecretDefinition implements Serializable {

        private static final long serialVersionUID = 1L;

        @Nullable
        public abstract String org();

        @JsonProperty(value = "name", required = true)
        public abstract String name();

        @Nullable
        public abstract String password();

        public static ImmutableSecretDefinition.Builder builder() {
            return ImmutableSecretDefinition.builder();
        }

        public String toString() {
            return ToStringHelper.prefix("")
                    .add("org", org())
                    .add("name", name())
                    .add("password", password() != null ? "***" : null)
                    .toString();
        }
    }

    static String hideSensitiveData(String url) {
        try {
            URL u = new URL(url);
            if (u.getUserInfo() == null) {
                return url;
            }

            return url.replace(u.getUserInfo(), "***");
        } catch (Exception e) {
            return url;
        }
    }
}
