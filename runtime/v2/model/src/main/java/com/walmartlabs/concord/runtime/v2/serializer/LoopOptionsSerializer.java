package com.walmartlabs.concord.runtime.v2.serializer;

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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.walmartlabs.concord.runtime.v2.model.Loop;

import java.io.IOException;
import java.util.Map;

public class LoopOptionsSerializer extends StdSerializer<Loop> {

    private static final long serialVersionUID = 1L;

    public LoopOptionsSerializer() {
        this(null);
    }

    public LoopOptionsSerializer(Class<Loop> t) {
        super(t);
    }

    @Override
    public void serialize(Loop value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();

        gen.writeObjectField("mode", value.mode().name().toLowerCase());
        gen.writeObjectField("items", value.items());

        for (Map.Entry<String, Object> e : value.options().entrySet()) {
            gen.writeObjectField(e.getKey(), e.getValue());
        }

        gen.writeEndObject();
    }
}
