package com.walmartlabs.concord.rpc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import java.nio.file.Path;

public class JobEntry {

    private final String instanceId;
    private final JobType jobType;
    private final Path payload;

    public JobEntry(String instanceId, JobType jobType, Path payload) {
        this.instanceId = instanceId;
        this.jobType = jobType;
        this.payload = payload;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public JobType getJobType() {
        return jobType;
    }

    public Path getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "JobEntry{" +
                "instanceId='" + instanceId + '\'' +
                ", jobType=" + jobType +
                ", payload=" + payload +
                '}';
    }
}
