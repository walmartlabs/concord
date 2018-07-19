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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class StreamProcessors {

    private final Map<String, StreamProcessor> processors = new HashMap<>();

    public StreamProcessors add(String itemPath, StreamProcessor processor) {
        StreamProcessor old = processors.put(itemPath, processor);
        if (old != null) {
            throw new IllegalArgumentException("Processor already exists: " + itemPath);
        }
        return this;
    }

    public InputStream process(String itemPath, InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        return getProcessor(itemPath).process(in);
    }

    private StreamProcessor getProcessor(String filePath) {
        return processors.getOrDefault(filePath, in -> in);
    }
}
