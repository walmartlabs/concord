package com.walmartlabs.concord.project.model;

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

import io.takari.bpm.model.SourceMap;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class Trigger implements Serializable {

    private final String name;
    private final String entryPoint;
    private final List<String> activeProfiles;
    private final Map<String, Object> arguments;
    private final Map<String, Object> params;
    private final SourceMap sourceMap;

    public Trigger(String name, String entryPoint, List<String> activeProfiles, Map<String, Object> arguments, Map<String, Object> params, SourceMap sourceMap) {
        this.name = name;
        this.entryPoint = entryPoint;
        this.activeProfiles = activeProfiles;
        this.arguments = arguments;
        this.params = params;
        this.sourceMap = sourceMap;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public List<String> getActiveProfiles() {
        return activeProfiles;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public SourceMap getSourceMap() {
        return sourceMap;
    }

    @Override
    public String toString() {
        return "Trigger{" +
                "name='" + name + '\'' +
                ", entryPoint='" + entryPoint + '\'' +
                ", activeProfiles=" + activeProfiles +
                ", arguments=" + arguments +
                ", params=" + params +
                ", sourceMap=" + sourceMap +
                '}';
    }
}
