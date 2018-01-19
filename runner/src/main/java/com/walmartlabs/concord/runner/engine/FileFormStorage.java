package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormStorage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FileFormStorage implements FormStorage {

    private final Path dir;

    public FileFormStorage(Path dir) {
        this.dir = dir;
    }

    @Override
    public void save(Form form) throws ExecutionException {
        UUID id = form.getFormInstanceId();
        Path p = dir.resolve(id.toString());
        try {
            Path tmp = IOUtils.createTempFile(id.toString(), "form");
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tmp))) {
                out.writeObject(form);
            }

            Files.move(tmp, p, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ExecutionException("Error while saving a form", e);
        }
    }

    @Override
    public void complete(UUID formInstanceId) throws ExecutionException {
        throw new IllegalStateException("Shouldn't be called from the runner's side");
    }

    @Override
    public Form get(UUID formInstanceId) throws ExecutionException {
        Path p = dir.resolve(formInstanceId.toString());
        if (!Files.exists(p)) {
            return null;
        }

        try (ObjectInputStream out = new ObjectInputStream(Files.newInputStream(p))) {
            return (Form) out.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new ExecutionException("Error while reading a form", e);
        }
    }
}
