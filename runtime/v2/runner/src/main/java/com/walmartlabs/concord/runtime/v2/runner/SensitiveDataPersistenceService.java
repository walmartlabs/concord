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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.ExecutionListener;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;

import javax.inject.Inject;
import java.util.Set;

public class SensitiveDataPersistenceService implements ExecutionListener {

    private final ObjectMapper objectMapper;
    private final PersistenceService persistenceService;

    @Inject
    public SensitiveDataPersistenceService(ObjectMapper objectMapper, PersistenceService persistenceService) {
        this.objectMapper = objectMapper;
        this.persistenceService = persistenceService;
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        if (SensitiveDataHolder.getInstance().empty()) {
            return;
        }

        persistenceService.persistSessionFile(Constants.Files.SENSITIVE_DATA_FILE_NAME,
                out -> objectMapper.writeValue(out, SensitiveDataHolder.getInstance().get()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void beforeProcessResume() {
        Set<String> sensitiveData = persistenceService.loadPersistedSessionFile(Constants.Files.SENSITIVE_DATA_FILE_NAME, is -> objectMapper.readValue(is, Set.class));
        if (sensitiveData != null) {
            SensitiveDataHolder.getInstance().addAll(sensitiveData);
        }
    }
}
