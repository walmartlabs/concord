package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;

public class FormServiceProvider implements Provider<FormService> {

    private final WorkingDirectory workingDirectory;

    @Inject
    public FormServiceProvider(WorkingDirectory workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public FormService get() {
        Path attachmentsDir = workingDirectory.getValue().resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        Path stateDir = attachmentsDir.resolve(Constants.Files.JOB_STATE_DIR_NAME);
        Path formsDir = stateDir.resolve(Constants.Files.JOB_FORMS_V2_DIR_NAME);
        return new FormService(formsDir);
    }
}
