package com.walmartlabs.concord.server.metrics;

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

import java.time.OffsetDateTime;

public class FailedTaskError {

    private String taskId;

    private String taskError;

    private OffsetDateTime taskErrorAt;

    public FailedTaskError(String taskId, String taskError, OffsetDateTime taskErrorAt) {
        this.taskId = taskId;
        this.taskError = taskError;
        this.taskErrorAt = taskErrorAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskError() {
        return taskError;
    }

    public void setTaskError(String taskError) {
        this.taskError = taskError;
    }

    public OffsetDateTime getTaskErrorAt() {
        return taskErrorAt;
    }

    public void setTaskErrorAt(OffsetDateTime taskErrorAt) {
        this.taskErrorAt = taskErrorAt;
    }

    @Override
    public String toString() {
        return "FailedTaskError{" +
                "taskId='" + taskId + '\'' +
                ", taskError='" + taskError + '\'' +
                ", taskErrorAt=" + taskErrorAt +
                '}';
    }
}
