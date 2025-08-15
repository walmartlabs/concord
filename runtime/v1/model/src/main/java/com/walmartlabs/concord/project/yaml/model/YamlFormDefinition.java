package com.walmartlabs.concord.project.yaml.model;

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

import io.takari.parc.Seq;

public class YamlFormDefinition implements YamlDefinition {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final Seq<YamlFormField> fields;

    public YamlFormDefinition(Seq<YamlFormField> fields) {
        this(null, fields);
    }

    public YamlFormDefinition(String name, Seq<YamlFormField> fields) {
        this.name = name;
        this.fields = fields;
    }

    @Override
    public String getName() {
        return name;
    }

    public Seq<YamlFormField> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return "YamlFormDefinition{" +
                "name='" + name + '\'' +
                ", fields=" + fields +
                '}';
    }
}
