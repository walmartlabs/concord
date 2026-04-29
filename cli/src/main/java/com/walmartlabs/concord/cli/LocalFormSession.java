package com.walmartlabs.concord.cli;

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

import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.runner.Runner;

import java.nio.file.Path;
import java.util.Collections;

final class LocalFormSession {

    static ProcessSnapshot resumePendingForms(Path workDir,
                                              Runner runner,
                                              ProcessSnapshot snapshot,
                                              LocalSuspendPersistence.ResumeMetadata metadata) throws Exception {
        return resumePendingForms(workDir, runner, snapshot, metadata, true);
    }

    static ProcessSnapshot resumePendingForms(Path workDir,
                                              Runner runner,
                                              ProcessSnapshot snapshot,
                                              LocalSuspendPersistence.ResumeMetadata metadata,
                                              boolean printHeader) throws Exception {

        var formPrompts = new LocalFormPrompts(workDir, printHeader);

        while (LocalSuspendPersistence.isSuspended(snapshot)) {
            var events = LocalSuspendPersistence.getEvents(snapshot);
            LocalSuspendPersistence.save(workDir, snapshot, metadata);

            var pendingForms = LocalFormState.syncPendingForms(workDir, events);
            if (pendingForms.isEmpty()) {
                return snapshot;
            }

            LocalFormState.assertSupported(workDir, pendingForms);

            var form = pendingForms.get(0);
            var input = formPrompts.prompt(form);
            snapshot = runner.resume(snapshot, Collections.singleton(form.eventName()), input);
        }

        LocalFormState.syncPendingForms(workDir, Collections.emptySet());
        return snapshot;
    }

    private LocalFormSession() {
    }
}
