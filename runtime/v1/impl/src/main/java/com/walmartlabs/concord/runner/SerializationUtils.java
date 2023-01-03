package com.walmartlabs.concord.runner;

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

import io.takari.bpm.api.Variables;
import io.takari.bpm.state.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;

public class SerializationUtils {

    private static final Logger log = LoggerFactory.getLogger(SerializationUtils.class);

    public static void serialize(OutputStream out, Object o) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(o);
        } catch (NotSerializableException e) {
            log.warn("Check if you're setting any not serializable values in your 'script' or 'task' steps.");
            reportNotSerializableItems(e, o);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void reportNotSerializableItems(NotSerializableException err, Object o) throws IOException {
        if (o instanceof Variables) {
            Variables v = (Variables) o;
            reportNotSerializableItems(err, v.asMap());
            return;
        } else if (o instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) o;
            for (Map.Entry<Object, Object> e : m.entrySet()) {
                if (!isSerializable(e.getKey())) {
                    log.warn("Not serializable key: {}", e.getKey());
                }

                Object v = e.getValue();
                if (v == null) {
                    continue;
                }

                if (!isSerializable(v)) {
                    Class k = v.getClass();
                    log.warn("Not serializable value: {} -> {} = {}", e.getKey(), k, v);
                }
            }
        } else if (o instanceof ProcessInstance) {
            ProcessInstance i = (ProcessInstance) o;
            reportNotSerializableItems(err, i.getVariables());
            return;
        }

        throw err;
    }

    private static boolean isSerializable(Object o) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
            oos.writeObject(o);
        } catch (IOException e) {
            return false;
        }

        return true;
    }
}
