package com.walmartlabs.concord.server.process.queue;

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

import java.util.List;
import java.util.UUID;

public final class WaitConditions {

    public static WaitCondition processCompletion(List<UUID> processes, String reason) {
        return WaitCondition.builder()
                .type(WaitType.PROCESS_COMPLETION)
                .reason(reason)
                .putPayload("processes", processes)
                .build();
    }

    public static WaitCondition none() {
        return WaitCondition.builder()
                .type(WaitType.NONE)
                .build();
    }

    private WaitConditions() {
    }
}
