package com.walmartlabs.concord.project.yaml;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.project.yaml.model.*;
import io.takari.parc.Input;
import io.takari.parc.Parser;
import io.takari.parc.Result;
import io.takari.parc.Result.Failure;
import io.takari.parc.Seq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YamlDeserializers {

    private static final JsonDeserializer<YamlStep> yamlStepDeserializer = new YamlStepDeserializer();
    private static final JsonDeserializer<YamlFormField> yamlFormFieldDeserializer = new YamlFormFieldDeserializer();
    private static final JsonDeserializer<YamlDefinitionFile> yamlDefinitionFileDeserializer = new YamlDefinitionFileDeserializer();
    private static final JsonDeserializer<YamlTrigger> yamlTriggerDeserializer = new YamlTriggerDeserializer();
    private static final JsonDeserializer<YamlImport> yamlImportDeserializer = new YamlImportDeserializer();

    public static JsonDeserializer<YamlStep> getYamlStepDeserializer() {
        return yamlStepDeserializer;
    }

    public static JsonDeserializer<YamlFormField> getYamlFormFieldDeserializer() {
        return yamlFormFieldDeserializer;
    }

    public static JsonDeserializer<YamlDefinitionFile> getYamlDefinitionFileDeserializer() {
        return yamlDefinitionFileDeserializer;
    }

    public static JsonDeserializer<YamlTrigger> getYamlTriggerDeserializer() {
        return yamlTriggerDeserializer;
    }

    public static JsonDeserializer<YamlImport> getYamlImportDeserializer() {
        return yamlImportDeserializer;
    }

    private static final class YamlStepDeserializer extends StdDeserializer<YamlStep> {

        private static final long serialVersionUID = 1L;

        protected YamlStepDeserializer() {
            super(YamlStep.class);
        }

        @Override
        public YamlStep deserialize(JsonParser json, DeserializationContext ctx) throws IOException {
            return parse(json, Grammar.getProcessStep());
        }
    }

    private static final class YamlFormFieldDeserializer extends StdDeserializer<YamlFormField> {

        private static final long serialVersionUID = 1L;

        protected YamlFormFieldDeserializer() {
            super(YamlFormField.class);
        }

        @Override
        public YamlFormField deserialize(JsonParser json, DeserializationContext ctx) throws IOException {
            return parse(json, Grammar.getFormField());
        }
    }

    private static final class YamlDefinitionFileDeserializer extends StdDeserializer<YamlDefinitionFile> {

        private static final long serialVersionUID = 1L;

        protected YamlDefinitionFileDeserializer() {
            super(YamlDefinitionFile.class);
        }

        @Override
        public YamlDefinitionFile deserialize(JsonParser json, DeserializationContext ctx) throws IOException {
            Seq<YamlDefinition> defs = parse(json, Grammar.getDefinitions());

            List<YamlDefinition> l = defs.toList();
            Map<String, YamlDefinition> m = new HashMap<>(l.size());
            for (YamlDefinition d : l) {
                m.put(d.getName(), d);
            }

            return new YamlDefinitionFile(m);
        }
    }

    private static final class YamlTriggerDeserializer extends StdDeserializer<YamlTrigger> {

        private static final long serialVersionUID = 1L;

        public YamlTriggerDeserializer() {
            super(YamlTrigger.class);
        }

        @Override
        public YamlTrigger deserialize(JsonParser json, DeserializationContext ctx) throws IOException {
            return parse(json, Grammar.getTrigger());
        }
    }

    private static final class YamlImportDeserializer extends StdDeserializer<YamlImport> {

        private static final long serialVersionUID = 1L;

        public YamlImportDeserializer() {
            super(YamlImport.class);
        }

        @Override
        public YamlImport deserialize(JsonParser json, DeserializationContext ctx) throws IOException {
            return parse(json, Grammar.getImport());
        }
    }

    private static <T> T parse(JsonParser json, Parser<Atom, T> parser) throws IOException {
        List<Atom> atoms = asSubtree(json);

        Input<Atom> in = new ListInput<>(atoms);
        Result<Atom, T> result = parser.parse(in);

        if (result.isFailure()) {
            Failure<Atom, ?> f = result.toFailure();
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

    private static JsonParseException toException(Failure<Atom, ?> f, JsonParser p, List<Atom> atoms) {
        JsonLocation loc = null;

        int pos = f.getPosition();
        if (pos >= 0) {
            Atom a = atoms.get(f.getPosition());
            loc = a.location;
        }

        return new JsonParseException(p, "Expected: " + f.getMessage() + ". Got " + atoms, loc);
    }

    private YamlDeserializers() {
    }
}
