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

public class YamlProcessDefinition implements YamlDefinition {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final Seq<YamlStep> steps;

    public YamlProcessDefinition(Seq<YamlStep> steps) {
        this(null, steps);
    }

    public YamlProcessDefinition(String name, Seq<YamlStep> steps) {
        this.name = name;
        this.steps = steps;
    }

    @Override
    public String getName() {
        return name;
    }

    public Seq<YamlStep> getSteps() {
        return steps;
    }

    @Override
    public String toString() {
        return "YamlProcessDefinition{" +
                "name='" + name + '\'' +
                ", steps=" + steps +
                '}';
    }
}
