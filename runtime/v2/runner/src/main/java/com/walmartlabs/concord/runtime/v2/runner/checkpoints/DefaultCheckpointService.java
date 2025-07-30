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

import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.common.ObjectInputStreamWithClassLoader;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.runner.vm.VMUtils;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.sdk.Constants.Request.RESUME_EVENTS_KEY;

public class DefaultCheckpointService implements CheckpointService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCheckpointService.class);

    private final WorkingDirectory workingDirectory;
    private final CheckpointUploader checkpointUploader;
    private final ClassLoader classLoader;

    @Inject
    public DefaultCheckpointService(WorkingDirectory workingDirectory,
                                    CheckpointUploader checkpointUploader,
                                    @Named("runtime") ClassLoader classLoader) {

        this.workingDirectory = workingDirectory;
        this.checkpointUploader = checkpointUploader;
        this.classLoader = classLoader;
    }

    @Override
    public void create(ThreadId threadId, UUID correlationId, String name, Runtime runtime, ProcessSnapshot snapshot) {
        validate(threadId, snapshot);

        UUID checkpointId = UUID.randomUUID();

        try (StateArchive archive = new StateArchive()) {
            // the goal here is to create a process state snapshot with
            // a "synthetic" event that can be used to continue the process
            // after the checkpoint step

            String resumeEventRef = checkpointId.toString();

            State state = clone(snapshot.vmState(), classLoader);
            state.setEventRef(threadId, resumeEventRef);
            state.setStatus(threadId, ThreadStatus.SUSPENDED);

            List<Frame> frames = state.getFrames(state.getRootThreadId());
            Frame rootFrame = frames.get(frames.size() - 1);
            VMUtils.putLocal(rootFrame, RESUME_EVENTS_KEY, Collections.singletonList(name));

            archive.withResumeEvent(resumeEventRef)
                    .withProcessState(ProcessSnapshot.builder()
                            .from(snapshot)
                            .vmState(state)
                            .build())
                    .withSystemDirectory(workingDirectory.getValue());

            try (TemporaryPath zip = archive.zip()) {
                checkpointUploader.upload(checkpointId, correlationId, name, zip.path());
            }
        } catch (Exception e) {
            throw new RuntimeException("Checkpoint upload error", e);
        }

        log.info("Checkpoint '{}' created", name);
    }

    private static void validate(ThreadId threadId, ProcessSnapshot snapshot) {
        State state = snapshot.vmState();

        String eventRef = state.getEventRefs().get(threadId);
        if (eventRef != null) {
            throw new IllegalStateException("Can't create a checkpoint, the current thread has an unprocessed eventRef: " + eventRef);
        }
    }

    @VisibleForTesting
    @SuppressWarnings("unchecked")
    static <T> T clone(T object, ClassLoader cl) throws Exception {
        byte[] ab;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            ab = baos.toByteArray();
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(ab);
             ObjectInputStream ois = new ObjectInputStreamWithClassLoader(bais, cl)) {
            return (T) ois.readObject();
        } catch (OptionalDataException odx) {
            log.error("Error while cloning an instance of {} (eof={}, length={}) using the provided class loader (name={}, class={})", object.getClass(), odx.eof, odx.length, cl.getName(), cl.getClass());
            throw odx;
        }
    }
}
