package com.walmartlabs.concord.server.org;

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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class ResourceAccessEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID teamId;

    @ConcordKey
    private final String orgName;

    @ConcordKey
    private final String teamName;

    @NotNull
    private final ResourceAccessLevel level;

    public ResourceAccessEntry(String teamName, ResourceAccessLevel level) {
        this(null, null, teamName, level);
    }


    public ResourceAccessEntry(String orgName, String teamName, ResourceAccessLevel level) {
        this(null, orgName, teamName, level);
    }

    @JsonCreator
    public ResourceAccessEntry(@JsonProperty("teamId") UUID teamId,
                               @JsonProperty("orgName") String orgName,
                               @JsonProperty("teamName") String teamName,
                               @JsonProperty("level") ResourceAccessLevel level) {

        this.teamId = teamId;
        this.orgName = orgName;
        this.teamName = teamName;
        this.level = level;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getTeamName() {
        return teamName;
    }

    public ResourceAccessLevel getLevel() {
        return level;
    }

    @Override
    public String toString() {
        return "ResourceAccessEntry{" +
                "teamId=" + teamId +
                ", orgName='" + orgName + '\'' +
                ", teamName='" + teamName + '\'' +
                ", level=" + level +
                '}';
    }
}
