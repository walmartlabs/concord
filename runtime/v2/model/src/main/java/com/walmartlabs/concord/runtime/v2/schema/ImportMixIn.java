package com.walmartlabs.concord.runtime.v2.schema;

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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.walmartlabs.concord.imports.Import;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ImportMixIn.MvnImportMixIn.class, name = "MVN Import"),
        @JsonSubTypes.Type(value = ImportMixIn.DirImportMixIn.class, name = "Dir Import"),
        @JsonSubTypes.Type(value = ImportMixIn.GitImportMixIn.class, name = "GIT Import")
})
public interface ImportMixIn {

    @JsonTypeName("MvnImport")
    interface MvnImportMixIn extends ImportMixIn {

        @JsonProperty("mvn")
        Import.MvnDefinition params();
    }

    @JsonTypeName("DirImport")
    interface DirImportMixIn extends ImportMixIn {

        @JsonProperty("dir")
        Import.DirectoryDefinition params();
    }

    @JsonTypeName("GitImport")
    interface GitImportMixIn extends ImportMixIn {

        @JsonProperty("git")
        Import.GitDefinition params();
    }
}
