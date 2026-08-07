package com.walmartlabs.concord.runtime.v25.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.util.List;

public final class ModelException extends IllegalArgumentException {

    private final List<Diagnostic> diagnostics;

    public ModelException(List<Diagnostic> diagnostics) {
        super(render(diagnostics));
        this.diagnostics = diagnostics.stream().sorted().toList();
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    private static String render(List<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return "Invalid Concord v2.5 definition";
        }
        var first = diagnostics.stream().sorted().findFirst().orElseThrow();
        var range = first.range();
        return "%s:%d:%d: %s at %s".formatted(range.source(), range.line(), range.column(),
                first.message(), first.path());
    }
}
