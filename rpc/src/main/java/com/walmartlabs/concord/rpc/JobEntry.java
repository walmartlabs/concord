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
import java.util.UUID;

public class JobEntry {

    private final UUID instanceId;
    private final Path payload;

    public JobEntry(UUID instanceId, Path payload) {
        this.instanceId = instanceId;
        this.payload = payload;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Path getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "JobEntry{" +
                "instanceId='" + instanceId + '\'' +
                ", payload=" + payload +
                '}';
    }
}
