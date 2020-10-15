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

import java.io.Serializable;
import java.util.Objects;

public class QueueRule implements Serializable {

    public static QueueRule empty() {
        return new QueueRule(null, null, null);
    }

    private final ConcurrentProcessRule concurrent;
    private final ForkDepthRule forkDepthRule;
    private final ProcessTimeoutRule processTimeoutRule;

    @JsonCreator
    public QueueRule(@JsonProperty("concurrent") ConcurrentProcessRule concurrent,
                     @JsonProperty("forkDepth") ForkDepthRule forkDepthRule,
                     @JsonProperty("processTimeout")  ProcessTimeoutRule processTimeoutRule) {

        this.concurrent = concurrent;
        this.forkDepthRule = forkDepthRule;
        this.processTimeoutRule = processTimeoutRule;
    }

    public ConcurrentProcessRule getConcurrent() {
        return concurrent;
    }

    public ForkDepthRule getForkDepthRule() {
        return forkDepthRule;
    }

    public ProcessTimeoutRule getProcessTimeoutRule() {
        return processTimeoutRule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueueRule queueRule = (QueueRule) o;
        return Objects.equals(concurrent, queueRule.concurrent) &&
                Objects.equals(forkDepthRule, queueRule.forkDepthRule) &&
                Objects.equals(processTimeoutRule, queueRule.processTimeoutRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(concurrent, forkDepthRule, processTimeoutRule);
    }

    @Override
    public final String toString() {
        return "QueueRule{" +
                "concurrent=" + concurrent +
                ", forkDepthRule=" + forkDepthRule +
                ", processTimeoutRule=" + processTimeoutRule +
                '}';
    }
}
