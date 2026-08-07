package com.walmartlabs.concord.runtime.v25.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessStatus;
import com.walmartlabs.concord.runtime.v25.runner.persistence.FileCheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25Codec;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPersistenceTest {

    @TempDir
    Path workDirectory;

    @Test
    void resumeStateUsesOnlyTheVersionedCheckpoint() throws Exception {
        var checkpoint = checkpointFile();
        var store = new FileCheckpointStore(checkpoint);
        var expected = state();
        store.save("suspend", expected);
        StateManager.saveProcessState(workDirectory, expected);

        assertEquals(expected, Main.loadPersistedState(store));
        assertEquals(Map.of("definition:1:0:0", 42L), Main.loadPersistedState(store).logSegments());

        try (var output = new ObjectOutputStream(Files.newOutputStream(checkpoint))) {
            output.writeObject(expected);
        }
        var error = assertThrows(State25Codec.StateFormatException.class, () -> Main.loadPersistedState(store));
        assertTrue(error.getMessage().contains("concord-v2.5 state header"));
    }

    @Test
    void resumeStateRejectsAnIncompatibleCheckpointVersion() throws Exception {
        var checkpoint = checkpointFile();
        Files.createDirectories(checkpoint.getParent());
        Files.write(checkpoint, new byte[]{'C', 'V', '2', '5', 0, 0, 0, 99});

        var error = assertThrows(State25Codec.StateFormatException.class,
                () -> Main.loadPersistedState(new FileCheckpointStore(checkpoint)));

        assertTrue(error.getMessage().contains("Unsupported concord-v2.5 state format 99"));
    }

    @Test
    void suspensionLifecycleDoesNotWriteGenericState() throws Exception {
        var stateDirectory = checkpointFile().getParent();
        StateManager.finalizeSuspendedState(workDirectory, Set.of("approval"));

        assertTrue(Files.exists(stateDirectory.resolve(Constants.Files.SUSPEND_MARKER_FILE_NAME)));
        assertFalse(Files.exists(stateDirectory.resolve("instance")));
    }

    @Test
    void rejectsResumeMarkerForAnOlderCheckpointGeneration() throws Exception {
        var store = new FileCheckpointStore(checkpointFile());
        store.save("suspend", state());
        StateManager.saveResumeEvent(workDirectory, "approval");
        Main.validateResumeMarkerGeneration(workDirectory, store);

        var newer = new State25(State25.CURRENT_FORMAT, "plan", "default", ProcessStatus.SUSPENDED,
                null, "suspend", Map.of(), 1L, state().root(), List.of(), List.of());
        store.save("checkpoint", newer);

        var error = assertThrows(IllegalStateException.class,
                () -> Main.validateResumeMarkerGeneration(workDirectory, store));
        assertTrue(error.getMessage().contains("older runtime v2.5 state generation"));
    }

    @Test
    void sensitiveDataChannelsRoundTrip() throws Exception {
        var persistence = new RunnerWiring.PersistenceService(workDirectory, RunnerWiring.objectMapper());
        var original = new RunnerWiring.SensitiveDataRegistry();
        original.add("persisted-secret");

        persistence.persistSessionFile(Constants.Files.SENSITIVE_DATA_FILE_NAME, original.get());
        persistence.persist(checkpointFile().resolveSibling("runtime-v2.5.state.secrets.json"), original.get());

        var fromSession = new RunnerWiring.SensitiveDataRegistry();
        persistence.mergeSessionFile(Constants.Files.SENSITIVE_DATA_FILE_NAME, fromSession);
        var fromSibling = new RunnerWiring.SensitiveDataRegistry();
        persistence.merge(checkpointFile().resolveSibling("runtime-v2.5.state.secrets.json"), fromSibling);

        assertEquals(Set.of("persisted-secret"), fromSession.get());
        assertEquals(Set.of("persisted-secret"), fromSibling.get());
    }

    private Path checkpointFile() {
        return workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME).resolve("runtime-v2.5.state");
    }

    private static State25 state() {
        var scope = new State25.ScopeState(1, null, "default", false, false, Map.of("value", "persisted"));
        var root = new State25.FiberState(1L, null, State25.FiberStatus.WAITING, 1, List.of(scope), List.of(), List.of());
        return new State25(State25.CURRENT_FORMAT, "plan", "default", ProcessStatus.SUSPENDED,
                null, "suspend", Map.of(), 0L, root, List.of(), List.of(),
                Map.of("definition:1:0:0", 42L));
    }
}
