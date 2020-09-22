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

        FormCallOptions o = value.getOptions();
        gen.writeObjectField("yield", o.yield());
        gen.writeObjectField("saveSubmittedBy", o.saveSubmittedBy());
        writeNotEmptyObjectField("runAs", o.runAs(), gen);
        writeNotEmptyObjectField("values", o.values(), gen);

        if (!o.fields().isEmpty()) {
            writeNotEmptyObjectField("fields", o.fields(), gen);
        } else if (o.fieldsExpression() != null) {
            gen.writeObjectField("fields", o.fieldsExpression());
        }

        gen.writeEndObject();
    }
}
