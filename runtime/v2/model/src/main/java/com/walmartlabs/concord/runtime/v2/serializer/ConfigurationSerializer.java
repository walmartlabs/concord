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
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;

import java.io.IOException;

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
        if (!value.dependencies().isEmpty()) {
            gen.writeObjectField("dependencies", value.dependencies());
        }
        if (!value.arguments().isEmpty()) {
            gen.writeObjectField("arguments", value.arguments());
        }
        if (!value.meta().isEmpty()) {
            gen.writeObjectField("meta", value.meta());
        }
        gen.writeObjectField("events", value.events());
        if (!value.requirements().isEmpty()) {
            gen.writeObjectField("requirements", value.requirements());
        }
        if (value.processTimeout() != null) {
            gen.writeObjectField("processTimeout", value.processTimeout());
        }
        if (value.exclusive() != null) {
            gen.writeObjectField("exclusive", value.exclusive());
        }
        if (!value.out().isEmpty()) {
            gen.writeObjectField("out", value.out());
        }
        gen.writeEndObject();
    }
}
