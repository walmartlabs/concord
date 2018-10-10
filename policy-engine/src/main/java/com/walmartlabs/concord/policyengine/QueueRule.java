package com.walmartlabs.concord.policyengine;

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
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;

public class QueueRule implements Serializable {

    public static QueueRule empty() {
        return new QueueRule(null, null, null, null, null, null);
    }

    private final ConcurrentProcessRule concurrent;
    private final QueueProcessRule process;
    private final QueueProcessRule processPerOrg;
    private final QueueProcessRule processPerProject;
    private final ForkDepthRule forkDepthRule;
    private final ProcessTimeoutRule processTimeoutRule;

    @JsonCreator
    public QueueRule(@JsonProperty("concurrent") ConcurrentProcessRule concurrent,
                     @JsonProperty("process") QueueProcessRule process,
                     @JsonProperty("processPerOrg") QueueProcessRule processPerOrg,
                     @JsonProperty("processPerProject") QueueProcessRule processPerProject,
                     @JsonProperty("forkDepth") ForkDepthRule forkDepthRule,
                     @JsonProperty("processTimeout")  ProcessTimeoutRule processTimeoutRule) {

        this.concurrent = concurrent;
        this.process = process;
        this.processPerOrg = processPerOrg;
        this.processPerProject = processPerProject;
        this.forkDepthRule = forkDepthRule;
        this.processTimeoutRule = processTimeoutRule;
    }

    public ConcurrentProcessRule getConcurrent() {
        return concurrent;
    }

    public QueueProcessRule getProcess() {
        return process;
    }

    public QueueProcessRule getProcessPerOrg() {
        return processPerOrg;
    }

    public QueueProcessRule getProcessPerProject() {
        return processPerProject;
    }

    public ForkDepthRule getForkDepthRule() {
        return forkDepthRule;
    }

    public ProcessTimeoutRule getProcessTimeoutRule() {
        return processTimeoutRule;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, Utils.NotNullToStringStyle.NOT_NULL_STYLE)
                .append("concurrent", concurrent)
                .append("process", process)
                .append("processPerOrg", processPerOrg)
                .append("processPerProject", processPerProject)
                .append("forkDepth", forkDepthRule)
                .append("processTimeout", processTimeoutRule)
                .toString();
    }
}
