package com.walmartlabs.concord.server.process.state;

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

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class ProcessMetadataManager {

    public static final String ON_FAILURE_MARKER_PATH = ".concord/meta/has_on_failure";
    public static final String ON_CANCEL_MARKER_PATH = ".concord/meta/has_on_cancel";

    private final ProcessStateManager stateManager;

    @Inject
    public ProcessMetadataManager(ProcessStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void addOnFailureMarker(UUID instanceId) {
        stateManager.insert(instanceId, ON_FAILURE_MARKER_PATH, "true".getBytes());
    }

    public void deleteOnFailureMarker(UUID instanceId) {
        stateManager.deleteFile(instanceId, ON_FAILURE_MARKER_PATH);
    }

    public void addOnCancelMarker(UUID instanceId) {
        stateManager.insert(instanceId, ON_CANCEL_MARKER_PATH, "true".getBytes());
    }

    public void deleteOnCancelMarker(UUID instanceId) {
        stateManager.deleteFile(instanceId, ON_CANCEL_MARKER_PATH);
    }
}
