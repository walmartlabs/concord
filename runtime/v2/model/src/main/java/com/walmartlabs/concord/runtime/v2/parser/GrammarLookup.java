package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.core.JsonToken;
import com.walmartlabs.concord.runtime.v2.exception.InvalidFieldDefinitionException;
import com.walmartlabs.concord.runtime.v2.exception.YamlProcessingException;
import io.takari.parc.Parser;
import io.takari.parc.Result;
import io.takari.parc.Seq;

import java.util.List;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.satisfyToken;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.testToken;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.value;
import static io.takari.parc.Combinators.many1;

public final class GrammarLookup {

    public static <O, T> Parser<Atom, O> lookup(String fieldName, YamlValueType<T> valueType,
                                                T value, Parser<Atom, O> valueMatchParser,
                                                Parser<Atom, O> elseParser) {
        return in -> {
            Result<Atom, List<KV<String, YamlValue>>> lookupValues = many1(fieldValue).map(Seq::toList).apply(in);
            if (lookupValues.isFailure()) {
                return elseParser.apply(in);
            }

            List<KV<String, YamlValue>> fields = lookupValues.toSuccess().getResult();
            YamlValue field = fields.stream().filter(f -> f.getKey().equals(fieldName)).findFirst().map(KV::getValue).orElse(null);
            if (field == null) {
                return elseParser.apply(in);
            }

            T actualValue;
            try {
                actualValue = field.getValue(valueType);
            } catch (YamlProcessingException e) {
                throw new InvalidFieldDefinitionException(fieldName, field.getLocation(), e);
            }

            if (value.equals(actualValue)) {
                return valueMatchParser.apply(in);
            }
            return elseParser.apply(in);
        };
    }

    private static final Parser<Atom, KV<String, YamlValue>> fieldValue =
            testToken(JsonToken.FIELD_NAME).bind(a -> satisfyToken(JsonToken.FIELD_NAME).then(
                    value.map(v -> new KV<>(a.name, v))));

    private GrammarLookup() {
    }
}
