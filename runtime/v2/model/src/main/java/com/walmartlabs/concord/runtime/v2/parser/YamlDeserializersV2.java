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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.exception.YamlProcessingException;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import io.takari.parc.Input;
import io.takari.parc.Parser;
import io.takari.parc.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class YamlDeserializersV2 {

    private static final JsonDeserializer<ProcessDefinition> processDefinitionDeserializer = new ProcessDefinitionDeserializer();

    public static JsonDeserializer<ProcessDefinition> getProcessDefinitionDeserializer() {
        return processDefinitionDeserializer;
    }

    private static final class ProcessDefinitionDeserializer extends StdDeserializer<ProcessDefinition> {

        private static final long serialVersionUID = 1L;

        protected ProcessDefinitionDeserializer() {
            super(Step.class);
        }

        @Override
        public ProcessDefinition deserialize(JsonParser json, DeserializationContext ctx) throws IOException {
            return parse(json, ProcessDefinitionGrammar.processDefinition);
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

    private static YamlProcessingException toException(Result.Failure<Atom, ?> f, JsonParser p, List<Atom> atoms) {
        Location loc = null;
        String got = "n/a";

        int pos = f.getPosition();
        if (pos >= 0) {
            Atom a = atoms.get(f.getPosition());
            loc = a.location;
            got = a.name;
        }

        return new YamlProcessingException(loc, "Expected: " + f.getMessage() + ". Got '" + got + "'");
    }

    private YamlDeserializersV2() {
    }
}
