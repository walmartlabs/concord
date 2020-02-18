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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.walmartlabs.concord.runtime.v2.model.Forms;
import com.walmartlabs.concord.runtime.v2.model.Step;
import io.takari.parc.Input;
import io.takari.parc.Parser;
import io.takari.parc.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class YamlDeserializersV2 {

    private static final JsonDeserializer<Step> stepDeserializer = new StepDeserializer();
    private static final JsonDeserializer<Forms> formsDeserializer = new FormsDeserializer();

    public static JsonDeserializer<Step> getStepDeserializer() {
        return stepDeserializer;
    }

    public static JsonDeserializer<Forms> getFormsDeserializer() {
        return formsDeserializer;
    }

    private static final class StepDeserializer extends StdDeserializer<Step> {

        protected StepDeserializer() {
            super(Step.class);
        }

        @Override
        public Step deserialize(JsonParser json, DeserializationContext ctx) throws IOException {
            return parse(json, GrammarV2.getProcessStep());
        }
    }

    private static final class FormsDeserializer extends StdDeserializer<Forms> {

        protected FormsDeserializer() {
            super(Forms.class);
        }

        @Override
        public Forms deserialize(JsonParser json, DeserializationContext ctx) throws IOException {
            return parse(json, FormsGrammar.forms);
        }
    }

    private static <T> T parse(JsonParser json, Parser<Atom, T> parser) throws IOException {
        List<Atom> atoms = asSubtree(json);

        Input<Atom> in = new ListInput<>(atoms);
        Result<Atom, T> result = parser.parse(in);

        if (result.isFailure()) {
            Result.Failure<Atom, ?> f = result.toFailure();
            throw toException(f, json, atoms);
        }

        return result.toSuccess().getResult();
    }

    private static List<Atom> asSubtree(JsonParser p) throws IOException {
        int level = 0;

        List<Atom> l = new ArrayList<>();
        while (p.currentToken() != null) {
            l.add(Atom.current(p));

            switch (p.currentToken()) {
                case START_OBJECT:
                case START_ARRAY:
                    level += 1;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    level -= 1;
                    break;
            }

            if (level <= 0) {
                break;
            }

            p.nextToken();
        }

        return l;
    }

    private static JsonParseException toException(Result.Failure<Atom, ?> f, JsonParser p, List<Atom> atoms) {
        JsonLocation loc = null;
        String got = "n/a";

        int pos = f.getPosition();
        if (pos >= 0) {
            Atom a = atoms.get(f.getPosition());
            loc = a.location;
            got = a.name;
        }

        return new JsonParseException(p, "Expected: " + f.getMessage() + ". Got '" + got + "'", loc);
    }

    private YamlDeserializersV2() {
    }
}
