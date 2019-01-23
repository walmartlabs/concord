package com.walmartlabs.concord.server;

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

public class VersionResponse implements Serializable {

    private final boolean ok = true;
    private final String version;
    private final String commitId;
    private final String env;

    @JsonCreator
    public VersionResponse(@JsonProperty("version") String version,
                           @JsonProperty("commitId") String commitId,
                           @JsonProperty("env") String env) {

        this.version = version;
        this.commitId = commitId;
        this.env = env;
    }

    public boolean isOk() {
        return ok;
    }

    public String getVersion() {
        return version;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getEnv() {
        return env;
    }

    @Override
    public String toString() {
        return "VersionResponse{" +
                "ok=" + ok +
                ", version='" + version + '\'' +
                ", commitId='" + commitId + '\'' +
                ", env='" + env + '\'' +
                '}';
    }
}
