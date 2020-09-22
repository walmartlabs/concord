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
import com.walmartlabs.concord.runtime.v2.model.GroupOfSteps;
import com.walmartlabs.concord.runtime.v2.model.GroupOfStepsOptions;

import java.io.IOException;

import static com.walmartlabs.concord.runtime.v2.serializer.SerializerUtils.writeNotEmptyObjectField;

public class GroupOfStepsSerializer extends StdSerializer<GroupOfSteps> {

    public GroupOfStepsSerializer() {
        this(null);
    }

    public GroupOfStepsSerializer(Class<GroupOfSteps> t) {
        super(t);
    }

    @Override
    public void serialize(GroupOfSteps value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();

        gen.writeFieldName("block");
        gen.writeObject(value.getSteps());

        GroupOfStepsOptions o = value.getOptions();
        writeNotEmptyObjectField("out", o.out(), gen);
        writeNotEmptyObjectField("error", o.errorSteps(), gen);
        writeNotEmptyObjectField("meta", o.meta(), gen);

        gen.writeEndObject();
    }
}
