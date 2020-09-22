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
import com.walmartlabs.concord.runtime.v2.model.Expression;

import java.io.IOException;

public class ExpressionStepSerializer extends StdSerializer<Expression> {

    public ExpressionStepSerializer() {
        this(null);
    }

    public ExpressionStepSerializer(Class<Expression> t) {
        super(t);
    }

    @Override
    public void serialize(Expression value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();

        gen.writeObjectField("expr", value.getExpr());

        if (value.getOptions().out() != null) {
            gen.writeObjectField("out", value.getOptions().out());
        }
        if (!value.getOptions().meta().isEmpty()) {
            gen.writeObjectField("meta", value.getOptions().meta());
        }
        if (!value.getOptions().errorSteps().isEmpty()) {
            gen.writeObjectField("error", value.getOptions().errorSteps());
        }

        gen.writeEndObject();
    }
}
