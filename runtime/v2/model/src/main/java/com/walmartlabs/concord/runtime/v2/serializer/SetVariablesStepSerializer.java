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
import com.walmartlabs.concord.runtime.v2.model.SetVariablesStep;

import java.io.IOException;

public class SetVariablesStepSerializer extends StdSerializer<SetVariablesStep> {

    private static final long serialVersionUID = 1L;

    public SetVariablesStepSerializer() {
        this(null);
    }

    public SetVariablesStepSerializer(Class<SetVariablesStep> t) {
        super(t);
    }

    @Override
    public void serialize(SetVariablesStep value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();

        gen.writeObjectField("set", value.getVars());
        gen.writeObject(value.getOptions());

        gen.writeEndObject();
    }
}
