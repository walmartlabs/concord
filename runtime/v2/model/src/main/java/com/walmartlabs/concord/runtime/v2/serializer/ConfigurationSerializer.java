package com.walmartlabs.concord.runtime.v2.serializer;

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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;

import java.io.IOException;

import static com.walmartlabs.concord.runtime.v2.serializer.SerializerUtils.writeNotEmptyObjectField;

public class ConfigurationSerializer extends StdSerializer<ProcessConfiguration> {

    public ConfigurationSerializer() {
        this(null);
    }

    public ConfigurationSerializer(Class<ProcessConfiguration> t) {
        super(t);
    }

    @Override
    public void serialize(ProcessConfiguration value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();

        gen.writeObjectField("runtime", value.runtime());
        gen.writeObjectField("debug", value.debug());
        gen.writeObjectField("entryPoint", value.entryPoint());
        writeNotEmptyObjectField("dependencies", value.dependencies(), gen);
        writeNotEmptyObjectField("arguments", value.arguments(), gen);
        writeNotEmptyObjectField("meta", value.meta(), gen);
        gen.writeObjectField("events", value.events());
        writeNotEmptyObjectField("requirements", value.requirements(), gen);

        if (value.processTimeout() != null) {
            gen.writeObjectField("processTimeout", value.processTimeout());
        }

        if (value.exclusive() != null) {
            gen.writeObjectField("exclusive", value.exclusive());
        }
        writeNotEmptyObjectField("out", value.out(), gen);

        gen.writeEndObject();
    }
}
