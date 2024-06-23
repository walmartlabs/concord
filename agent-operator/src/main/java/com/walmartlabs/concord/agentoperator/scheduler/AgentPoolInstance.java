package com.walmartlabs.concord.agentoperator.scheduler;

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

import com.walmartlabs.concord.agentoperator.crd.AgentPool;

public class AgentPoolInstance {

    public static AgentPoolInstance updateStatus(AgentPoolInstance i, Status status) {
        return new AgentPoolInstance(i.name, i.resource, status, i.targetSize, System.currentTimeMillis(), i.getLastScaleUpTimestamp(), i.getLastScaleDownTimeStamp());
    }

    public static AgentPoolInstance updateTargetSize(AgentPoolInstance i, int targetSize, long scaleUptimeStamp, long scaleDownTimeStamp) {
        return new AgentPoolInstance(i.name, i.resource, i.status, targetSize, System.currentTimeMillis(), scaleUptimeStamp, scaleDownTimeStamp);
    }

    private final String name;
    private final AgentPool resource;
    private final Status status;
    private final int targetSize;
    private final long lastUpdateTimestamp;
    private final long lastScaleUpTimestamp;
    private final long lastScaleDownTimeStamp;

    public AgentPoolInstance(String name, AgentPool resource, Status status, int targetSize, long lastUpdateTimestamp,
                             long lastScaleUpTimestamp, long lastScaleDownTimeStamp) {
        this.name = name;
        this.resource = resource;
        this.status = status;
        this.targetSize = targetSize;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.lastScaleUpTimestamp = lastScaleUpTimestamp;
        this.lastScaleDownTimeStamp = lastScaleDownTimeStamp;
    }

    public String getName() {
        return name;
    }

    public AgentPool getResource() {
        return resource;
    }

    public Status getStatus() {
        return status;
    }

    public int getTargetSize() {
        return targetSize;
    }

    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public long getLastScaleUpTimestamp() {
        return lastScaleUpTimestamp;
    }

    public long getLastScaleDownTimeStamp() {
        return lastScaleDownTimeStamp;
    }

    public enum Status {
        ACTIVE,
        DELETED
    }
}
