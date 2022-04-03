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
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.model.WithItems;

import java.io.IOException;
import java.util.Objects;

import static com.walmartlabs.concord.runtime.v2.serializer.SerializerUtils.*;

public class TaskCallStepSerializer extends StdSerializer<TaskCall> {

    private static final long serialVersionUID = 1L;

    public TaskCallStepSerializer() {
        this(null);
    }

    public TaskCallStepSerializer(Class<TaskCall> t) {
        super(t);
    }

    @Override
    public void serialize(TaskCall value, JsonGenerator gen, SerializerProvider provider) throws IOException {

        gen.writeStartObject();

        TaskCallOptions o = Objects.requireNonNull(value.getOptions());

        if ("log".equals(value.getName())) {
            gen.writeObjectField("log", o.input().get("msg"));
        } else {
            gen.writeObjectField("task", value.getName());

            writeNotEmptyObjectField("in", o.input(), gen);
            writeNotEmptyObjectField("in", o.inputExpression(), gen);
        }

        writeNotEmptyObjectField("out", o.out(), gen);
        writeNotEmptyObjectField("out", o.outExpr(), gen);

        if (o.withItems() != null) {
            WithItems items = Objects.requireNonNull(o.withItems());
            writeWithItems(items, gen);
        }

        writeLoop(o.loop(), gen);

        if (o.retry() != null) {
            gen.writeObjectField("retry", o.retry());
        }

        writeNotEmptyObjectField("error", o.errorSteps(), gen);
        writeNotEmptyObjectField("meta", o.meta(), gen);

        if (o.ignoreErrors()) {
            gen.writeObjectField("ignoreErrors", o.ignoreErrors());
        }

        gen.writeEndObject();
    }
}
