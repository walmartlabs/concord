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
import com.walmartlabs.concord.runtime.v2.model.FormField;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

public class FormFieldSerializer extends StdSerializer<FormField> {

    public FormFieldSerializer() {
        this(null);
    }

    public FormFieldSerializer(Class<FormField> t) {
        super(t);
    }

    @Override
    public void serialize(FormField value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeFieldName(value.name());

        gen.writeStartObject();
        if (value.label() != null) {
            gen.writeObjectField("label", value.label());
        }
        gen.writeObjectField("type", toType(value.type(), value.cardinality()));
        gen.writeObjectField("value", value.defaultValue());
        gen.writeObjectField("allow", value.allowedValue());
        if (!value.options().isEmpty()) {
            for (Map.Entry<String, Serializable> e : value.options().entrySet()) {
                gen.writeObjectField(e.getKey(), e.getValue());
            }
        }
        gen.writeEndObject();

        gen.writeEndObject();
    }

    private String toType(String type, com.walmartlabs.concord.forms.FormField.Cardinality cardinality) {
        switch (cardinality) {
            case ONE_AND_ONLY_ONE:
                return type;
            case ONE_OR_NONE:
                return type + "?";
            case AT_LEAST_ONE:
                return type + "+";
            case ANY:
                return type + "*";
            default:
                throw new IllegalArgumentException("Unknown cardinality: '" + cardinality + "'");
        }
    }
}
