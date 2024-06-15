package com.walmartlabs.concord.runtime.v2.runner.script;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.oracle.truffle.js.scriptengine.GraalJSEngineFactory;

import javax.script.Bindings;
import javax.script.ScriptEngine;

public final class ScriptEngineBindings {

    public static Bindings create(ScriptEngine engine, String language) {
        Bindings b = engine.createBindings();

        if (new GraalJSEngineFactory().getNames().contains(language)) {
            b.put("polyglot.js.allowAllAccess", true);
        }
        
        return b;
    }

    private ScriptEngineBindings() {
    }
}
