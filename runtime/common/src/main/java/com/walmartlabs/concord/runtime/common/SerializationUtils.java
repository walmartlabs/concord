package com.walmartlabs.concord.runtime.common;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public final class SerializationUtils {

    private static final Logger log = LoggerFactory.getLogger(SerializationUtils.class);

    public static void serialize(OutputStream out, Serializable o) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(o);
        } catch (NotSerializableException e) {
            log.warn("Check if you're setting any not serializable values in your 'script', 'task' or 'form' steps."); // TODO include the original error
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(InputStream in, Class<T> expectedType) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(in)) {
            return (T) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Can't deserialize a value into " + expectedType + ": " + e.getMessage(), e);
        }
    }

    public static boolean isSerializable(Object o) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
            oos.writeObject(o);
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    private SerializationUtils() {
    }
}
