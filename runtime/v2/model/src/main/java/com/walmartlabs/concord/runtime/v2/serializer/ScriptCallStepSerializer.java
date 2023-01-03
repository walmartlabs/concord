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
import com.walmartlabs.concord.runtime.v2.model.ScriptCall;
import com.walmartlabs.concord.runtime.v2.model.ScriptCallOptions;
import com.walmartlabs.concord.runtime.v2.model.WithItems;

import java.io.IOException;
import java.util.Objects;

import static com.walmartlabs.concord.runtime.v2.serializer.SerializerUtils.*;

public class ScriptCallStepSerializer extends StdSerializer<ScriptCall> {

    private static final long serialVersionUID = 1L;

    public ScriptCallStepSerializer() {
        this(null);
    }

    public ScriptCallStepSerializer(Class<ScriptCall> t) {
        super(t);
    }

    @Override
    public void serialize(ScriptCall value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();

        gen.writeObjectField("script", value.getLanguageOrRef());
        serializeOptions(value.getOptions(), gen);

        gen.writeEndObject();
    }

    private static void serializeOptions(ScriptCallOptions options, JsonGenerator gen) throws IOException {
        if (options == null) {
            return;
        }

        if (options.body() != null) {
            gen.writeObjectField("body", options.body());
        }

        writeNotEmptyObjectField("in", options.input(), gen);
        writeNotEmptyObjectField("in", options.inputExpression(), gen);

        writeNotEmptyObjectField("out", options.out(), gen);
        writeNotEmptyObjectField("out", options.outExpr(), gen);

        if (options.withItems() != null) {
            WithItems items = Objects.requireNonNull(options.withItems());
            writeWithItems(items, gen);
        }

        writeLoop(options.loop(), gen);

        if (options.retry() != null) {
            gen.writeObjectField("retry", options.retry());
        }

        writeNotEmptyObjectField("error", options.errorSteps(), gen);
        writeNotEmptyObjectField("meta", options.meta(), gen);
    }
}
