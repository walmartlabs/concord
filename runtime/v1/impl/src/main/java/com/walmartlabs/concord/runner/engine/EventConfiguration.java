package com.walmartlabs.concord.runner.engine;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class EventConfiguration {

    private final Collection<String> DEFAULT_IN_VARS_BLACKLIST = Arrays.asList(
            "apiKey",
            "apiToken",
            "password",
            "privateKey",
            "vaultPassword");

    private boolean recordEvents = true;
    private int batchFlushInterval = 15;
    private int batchSize = 1;
    private boolean truncateInVars = true;
    private boolean recordTaskInVars = false;
    private boolean recordTaskOutVars = false;
    private boolean truncateOutVars = true;
    private boolean updateMetaOnAllEvents = true;
    private Collection<String> inVarsBlacklist = DEFAULT_IN_VARS_BLACKLIST;
    private Collection<String> outVarsBlacklist = Collections.emptyList();
    private int truncateMaxStringLength = 1024;
    private int truncateMaxArrayLength = 32;
    private int truncateMaxDepth = 32;

    public boolean isRecordEvents() {
        return recordEvents;
    }

    public void setRecordEvents(boolean recordEvents) {
        this.recordEvents = recordEvents;
    }

    public int getBatchFlushInterval() {
        return batchFlushInterval;
    }

    public void setBatchFlushInterval(int batchFlushInterval) {
        this.batchFlushInterval = batchFlushInterval;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isRecordTaskInVars() {
        return recordTaskInVars;
    }

    public void setRecordTaskInVars(boolean recordTaskInVars) {
        this.recordTaskInVars = recordTaskInVars;
    }

    public boolean isRecordTaskOutVars() {
        return recordTaskOutVars;
    }

    public void setRecordTaskOutVars(boolean recordTaskOutVars) {
        this.recordTaskOutVars = recordTaskOutVars;
    }

    public Collection<String> getInVarsBlacklist() {
        return inVarsBlacklist;
    }

    public void setInVarsBlacklist(Collection<String> inVarsBlacklist) {
        this.inVarsBlacklist = inVarsBlacklist;
    }

    public Collection<String> getOutVarsBlacklist() {
        return outVarsBlacklist;
    }

    public void setOutVarsBlacklist(Collection<String> outVarsBlacklist) {
        this.outVarsBlacklist = outVarsBlacklist;
    }

    public boolean isTruncateInVars() {
        return truncateInVars;
    }

    public boolean isTruncateOutVars() {
        return truncateOutVars;
    }

    public boolean isUpdateMetaOnAllEvents() {
        return updateMetaOnAllEvents;
    }

    public void setUpdateMetaOnAllEvents(boolean updateMetaOnAllEvents) {
        this.updateMetaOnAllEvents = updateMetaOnAllEvents;
    }

    public int getTruncateMaxStringLength() {
        return truncateMaxStringLength;
    }

    public int getTruncateMaxArrayLength() {
        return truncateMaxArrayLength;
    }

    public int getTruncateMaxDepth() {
        return truncateMaxDepth;
    }

    public void setTruncateInVars(boolean truncateInVars) {
        this.truncateInVars = truncateInVars;
    }

    public void setTruncateOutVars(boolean truncateOutVars) {
        this.truncateOutVars = truncateOutVars;
    }

    public void setTruncateMaxStringLength(int truncateMaxStringLength) {
        this.truncateMaxStringLength = truncateMaxStringLength;
    }

    public void setTruncateMaxArrayLength(int truncateMaxArrayLength) {
        this.truncateMaxArrayLength = truncateMaxArrayLength;
    }

    public void setTruncateMaxDepth(int truncateMaxDepth) {
        this.truncateMaxDepth = truncateMaxDepth;
    }
}
