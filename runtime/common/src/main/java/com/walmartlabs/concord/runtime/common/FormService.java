package com.walmartlabs.concord.runtime.common;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.forms.Form;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

// TODO make it an interface?
public class FormService {

    private final Path dir;

    public FormService(Path dir) {
        this.dir = dir;

        try {
            if (Files.notExists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void save(Form form) {
        try {
            Path p = IOUtils.assertInPath(dir, form.name());
            Path tmp = IOUtils.createTempFile(form.name(), "form");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                SerializationUtils.serialize(out, form);
            }
            Files.move(tmp, p, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Error while saving a form ('" + form.name() + "') : " + e.getMessage(), e);
        }
    }

    public List<Form> list() {
        try (Stream<Path> list = Files.list(dir)) {
            return list.map(this::read)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Error while getting a list of forms: " + e.getMessage(), e);
        }
    }

    private Form read(Path p) {
        try (InputStream in = Files.newInputStream(p)) {
            return SerializationUtils.deserialize(in, Form.class);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading a form " + p + ": " + e.getMessage(), e);
        }
    }
}
