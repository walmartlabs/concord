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
import com.walmartlabs.concord.runtime.v2.model.FormCall;
import com.walmartlabs.concord.runtime.v2.model.FormCallOptions;

import java.io.IOException;

import static com.walmartlabs.concord.runtime.v2.serializer.SerializerUtils.writeNotEmptyObjectField;

public class FormCallStepSerializer extends StdSerializer<FormCall> {

    private static final long serialVersionUID = 1L;

    public FormCallStepSerializer() {
        this(null);
    }

    public FormCallStepSerializer(Class<FormCall> t) {
        super(t);
    }

    @Override
    public void serialize(FormCall value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeObjectField("form", value.getName());
        serializeOptions(value.getOptions(), gen);
        gen.writeEndObject();
    }

    private static void serializeOptions(FormCallOptions options, JsonGenerator gen) throws IOException {
        if (options == null) {
            return;
        }

        gen.writeObjectField("yield", options.isYield());
        gen.writeObjectField("saveSubmittedBy", options.saveSubmittedBy());

        if (!options.runAs().isEmpty()) {
            writeNotEmptyObjectField("runAs", options.runAs(), gen);
        } else {
            gen.writeObjectField("runAs", options.runAsExpression());
        }

        if (!options.values().isEmpty()) {
            writeNotEmptyObjectField("values", options.values(), gen);
        } else if (options.valuesExpression() != null) {
            gen.writeObjectField("values", options.valuesExpression());
        }

        if (!options.fields().isEmpty()) {
            writeNotEmptyObjectField("fields", options.fields(), gen);
        } else if (options.fieldsExpression() != null) {
            gen.writeObjectField("fields", options.fieldsExpression());
        }
    }
}
