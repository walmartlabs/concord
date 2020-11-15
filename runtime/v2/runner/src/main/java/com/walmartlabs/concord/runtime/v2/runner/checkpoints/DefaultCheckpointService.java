package com.walmartlabs.concord.runtime.v2.runner.checkpoints;

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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.ThreadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DefaultCheckpointService implements CheckpointService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCheckpointService.class);

    private final InstanceId instanceId;
    private final WorkingDirectory workingDirectory;
    private final ApiClient apiClient;
    private final ApiConfiguration apiConfiguration;

    @Inject
    public DefaultCheckpointService(InstanceId instanceId,
                                    WorkingDirectory workingDirectory,
                                    RunnerConfiguration configuration,
                                    ApiClient apiClient) {

        this.instanceId = instanceId;
        this.workingDirectory = workingDirectory;
        this.apiConfiguration = configuration.api();
        this.apiClient = apiClient;
    }

    @Override
    public void create(ThreadId threadId, String name, Runtime runtime, ProcessSnapshot snapshot) {
        validate(threadId, snapshot);

        UUID checkpointId = UUID.randomUUID();

        try (StateArchive archive = new StateArchive()) {
            // the goal here is to create a process state snapshot with
            // a "synthetic" event that can be used to continue the process
            // after the checkpoint step

            String resumeEventRef = checkpointId.toString();

            State state = clone(snapshot.vmState());
            state.setEventRef(threadId, resumeEventRef);
            state.setStatus(threadId, ThreadStatus.SUSPENDED);

            archive.withResumeEvent(resumeEventRef)
                    .withProcessState(ProcessSnapshot.builder()
                            .from(snapshot)
                            .vmState(state)
                            .build())
                    .withSystemDirectory(workingDirectory.getValue());

            try (TemporaryPath zip = archive.zip()) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", checkpointId);
                data.put("name", name);
                data.put("data", zip.path());

                uploadCheckpoint(instanceId.getValue(), data);
            }
        } catch (Exception e) {
            throw new RuntimeException("Checkpoint upload error", e);
        }

        log.info("create ['{}'] -> done", name);
    }

    private void uploadCheckpoint(UUID instanceId, Map<String, Object> data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/checkpoint";

        ClientUtils.withRetry(apiConfiguration.retryCount(), apiConfiguration.retryInterval(), () -> {
            ClientUtils.postData(apiClient, path, data, null);
            return null;
        });
    }

    private static void validate(ThreadId threadId, ProcessSnapshot snapshot) {
        State state = snapshot.vmState();

        String eventRef = state.getEventRefs().get(threadId);
        if (eventRef != null) {
            throw new IllegalStateException("Can't create a checkpoint, the current thread has an unprocessed eventRef: " + eventRef);
        }
    }

    private static State clone(State state) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(state);
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (State) ois.readObject();
        }
    }
}
