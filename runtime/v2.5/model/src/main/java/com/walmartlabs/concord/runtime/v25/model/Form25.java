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

import com.walmartlabs.concord.runtime.model.Form;
import com.walmartlabs.concord.runtime.model.FormField;
import com.walmartlabs.concord.runtime.model.SourceMap;

import java.io.Serializable;
import java.util.List;

/** A top-level form with either declared fields or an expression yielding fields. */
public record Form25(String name, List<FormField> fields, String fieldsExpression,
                     SourceRange sourceRange) implements Form, Serializable {

    public Form25 {
        fields = Values.list(fields);
        if (fieldsExpression != null && !fields.isEmpty()) {
            throw new IllegalArgumentException("A form cannot define both fields and a fields expression");
        }
    }

    public boolean dynamic() {
        return fieldsExpression != null;
    }

    @Override
    public SourceMap location() {
        return sourceRange.toSourceMap();
    }
}
