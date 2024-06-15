package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.common.SerializationUtils;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

public class CliCheckpointService implements CheckpointService {

    @Override
    public void create(ThreadId threadId, UUID correlationId, String name, Runtime runtime, ProcessSnapshot snapshot) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SerializationUtils.serialize(baos, snapshot.vmState());
        } catch (Exception e) {
            throw new RuntimeException("Checkpoint create error", e);
        }

        System.out.println("Checkpoint '" +  name +  "' ignored");
    }
}
