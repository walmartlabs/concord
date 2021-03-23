package com.walmartlabs.concord.server.process.waits;

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

import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Named
@Singleton
public class NoneWaitHandler implements ProcessWaitHandler<NoneCondition> {

    private static final Set<ProcessStatus> STATUSES = new HashSet<>(Arrays.asList(ProcessStatus.values()));

    @Override
    public WaitType getType() {
        return WaitType.NONE;
    }

    @Override
    public Set<ProcessStatus> getProcessStatuses() {
        return STATUSES;
    }

    @Override
    public NoneCondition process(ProcessKey processKey, ProcessStatus processStatus, NoneCondition waits) {
        return null;
    }
}
