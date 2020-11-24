package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;

@Named
public class StateExportProcessor implements PayloadProcessor {

    private final ProcessStateManager stateManager;

    @Inject
    public StateExportProcessor(ProcessStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        try (TemporaryPath workDir = IOUtils.tempDir("payload")) {
            if (!stateManager.export(processKey, copyTo(workDir.path()))) {
                throw new ProcessException(processKey, "Can't export '" + processKey + "', state snapshot not found");
            }

            payload = payload.putHeader(Payload.WORKSPACE_DIR, workDir.path());

            return chain.process(payload);
        } catch (ProcessException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessException(processKey, "Can't export '" + processKey + "' state: " + e.getMessage());
        }
    }
}
