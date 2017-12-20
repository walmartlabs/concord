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

public class CancelJobCommand implements Command {

    private final String instanceId;

    public CancelJobCommand(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public CommandType getType() {
        return CommandType.CANCEL_JOB;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return "CancelJobCommand{" +
                "instanceId='" + instanceId + '\'' +
                '}';
    }
}
