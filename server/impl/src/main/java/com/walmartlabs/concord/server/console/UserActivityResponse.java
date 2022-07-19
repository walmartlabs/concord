package com.walmartlabs.concord.server.console;

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
import com.walmartlabs.concord.server.process.ProcessEntry;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_EMPTY)
public class UserActivityResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    public static class ProjectProcesses implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String projectName;
        private final int running;

        @JsonCreator
        public ProjectProcesses(@JsonProperty("projectName") String projectName,
                                @JsonProperty("running") int running) {
            this.projectName = projectName;
            this.running = running;
        }

        public String getProjectName() {
            return projectName;
        }

        public int getRunning() {
            return running;
        }
    }

    private final Map<String, Integer> processStats;
    private final Map<String, List<ProjectProcesses>> orgProcesses;
    private final List<ProcessEntry> processes;

    @JsonCreator
    public UserActivityResponse(@JsonProperty("processStats") Map<String, Integer> processStats,
                                @JsonProperty("orgProcesses") Map<String, List<ProjectProcesses>> orgProcesses,
                                @JsonProperty("processes") List<ProcessEntry> processes) {

        this.processStats = processStats;
        this.orgProcesses = orgProcesses;
        this.processes = processes;
    }

    public Map<String, Integer> getProcessStats() {
        return processStats;
    }

    public Map<String, List<ProjectProcesses>> getOrgProcesses() {
        return orgProcesses;
    }

    public List<ProcessEntry> getProcesses() {
        return processes;
    }
}
