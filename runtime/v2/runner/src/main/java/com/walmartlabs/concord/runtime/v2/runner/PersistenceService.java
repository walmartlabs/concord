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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

public interface PersistenceService {

    <T extends Serializable> T load(String storageName, Class<T> expectedType);

    void save(String storageName, Serializable object) throws IOException;

    void persistFile(String name, Writer writer);

    void persistFile(String name, Writer writer, OpenOption... options);

    void persistSessionFile(String name, Writer writer);

    <T> T loadPersistedFile(String name, Converter<InputStream, T> converter);

    <T> T loadPersistedSessionFile(String name, Converter<InputStream, T> converter);

    void deletePersistedFile(String name) throws IOException;

    interface Writer {

        void write(OutputStream out) throws IOException;
    }

    interface Converter<T, R> {

        R apply(T t) throws Exception;
    }
}
