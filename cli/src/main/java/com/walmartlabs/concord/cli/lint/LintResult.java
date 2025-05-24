package com.walmartlabs.concord.cli.lint;

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

import com.walmartlabs.concord.runtime.model.SourceMap;

public class LintResult {

    public static LintResult error(SourceMap sourceMap, String message) {
        return new LintResult(Type.ERROR, sourceMap, message);
    }

    private final Type type;
    private final SourceMap sourceMap;
    private final String message;

    public LintResult(Type type, SourceMap sourceMap, String message) {
        this.type = type;
        this.sourceMap = sourceMap;
        this.message = message;
    }

    public Type getType() {
        return type;
    }

    public SourceMap getSourceMap() {
        return sourceMap;
    }

    public String getMessage() {
        return message;
    }

    public enum Type {
        WARNING,
        ERROR
    }
}
