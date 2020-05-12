package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DefaultPersistenceService implements PersistenceService {

    private final WorkingDirectory workingDirectory;

    @Inject
    public DefaultPersistenceService(WorkingDirectory workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public <T extends Serializable> T load(String storageName, Class<T> expectedType) {
        return StateManager.load(workingDirectory.getValue(), storageName, expectedType);
    }

    @Override
    public void save(String storageName, Serializable object) throws IOException {
        StateManager.persist(workingDirectory.getValue(), storageName, object);
    }

    @Override
    public void persistFile(String name, Writer writer) {
        Path storeDir = workingDirectory.getValue()
                .resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);

        try {
            if (!Files.exists(storeDir)) {
                Files.createDirectories(storeDir);
            }

            Path p = storeDir.resolve(name);
            try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(out);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error persisting file '" + name + "': " + e.getMessage());
        }

    }
}
