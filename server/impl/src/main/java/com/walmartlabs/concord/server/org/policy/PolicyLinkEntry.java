package com.walmartlabs.concord.server.org.policy;

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

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class PolicyLinkEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @ConcordKey
    private final String orgName;

    @ConcordKey
    private final String projectName;

    @ConcordKey
    private final String userName;

    private final String userDomain;

    public PolicyLinkEntry() {
        this(null, null, null, null);
    }

    public PolicyLinkEntry(String orgName) {
        this(orgName, null, null, null);
    }

    @JsonCreator
    public PolicyLinkEntry(@JsonProperty("orgName") String orgName,
                           @JsonProperty("projectName") String projectName,
                           @JsonProperty("userName") String userName,
                           @JsonProperty("userDomain") String userDomain) {

        this.orgName = orgName;
        this.projectName = projectName;
        this.userName = userName;
        this.userDomain = userDomain;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserDomain() {
        return userDomain;
    }

    @Override
    public String toString() {
        return "PolicyLinkEntry{" +
                "orgName='" + orgName + '\'' +
                ", projectName='" + projectName + '\'' +
                ", userName='" + userName + '\'' +
                ", userDomain='" + userDomain + '\'' +
                '}';
    }
}
