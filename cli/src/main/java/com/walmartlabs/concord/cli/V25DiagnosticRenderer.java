package com.walmartlabs.concord.cli;

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

import com.walmartlabs.concord.runtime.v25.model.ModelException;

import java.io.PrintStream;

final class V25DiagnosticRenderer {

    private V25DiagnosticRenderer() {
    }

    static void print(ModelException error, PrintStream stream) {
        for (var diagnostic : error.diagnostics()) {
            var range = diagnostic.range();
            stream.printf("%s %s at %s:%d:%d-%d:%d (%s): %s%n",
                    diagnostic.severity(), diagnostic.code(), range.source(), range.line(), range.column(),
                    range.endLine(), range.endColumn(), diagnostic.path(), diagnostic.message());
            if (diagnostic.suggestion() != null && !diagnostic.suggestion().isBlank()) {
                stream.printf("  suggestion: %s%n", diagnostic.suggestion());
            }
        }
    }
}
