package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.ListIterator;

public class StateImportingProcessor implements PayloadProcessor {

    private static final String PASS_THROUGH_PATH = "forms/";

    private final ProcessStateManager stateManager;

    @Inject
    public StateImportingProcessor(ProcessStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        List<Snapshot> snapshots = payload.getHeader(Payload.REPOSITORY_SNAPSHOT);
        stateManager.replacePath(processKey, workspace, (p, attrs) -> filter(p, attrs, snapshots, workspace));

        return chain.process(payload);
    }

    /**
     * Return {@code true} for each {@code p} that must be included into the process state.
     */
    private boolean filter(Path p, BasicFileAttributes attrs, List<Snapshot> snapshots, Path workDir) {
        // those files we need to store in the DB regardless of whether they are from a repository or not
        // e.g. custom forms: we need those files in the DB in order to serve custom form files
        if (workDir.relativize(p).toString().startsWith(PASS_THROUGH_PATH)) {
            return true;
        }

        Path resolved = p;
        if (!p.isAbsolute()) {
            resolved = workDir.resolve(p).normalize();
        }

        // we never store symlinks in the DB
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        if (snapshots != null) {
            ListIterator<Snapshot> it = snapshots.listIterator(snapshots.size());
            while (it.hasPrevious()) {
                Snapshot s = it.previous();
                if (s.contains(p)) {
                    return s.isModified(p, attrs);
                }
            }
        }

        return true;
    }
}
