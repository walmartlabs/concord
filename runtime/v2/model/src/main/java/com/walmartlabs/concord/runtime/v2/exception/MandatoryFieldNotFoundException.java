package com.walmartlabs.concord.runtime.v2.exception;

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

import java.util.Collections;
import java.util.List;

public class MandatoryFieldNotFoundException extends YamlProcessingException {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = 2604568258403971784L;

    private final List<String> fields;

    public MandatoryFieldNotFoundException(String field) {
        this(Collections.singletonList(field));
    }

    public MandatoryFieldNotFoundException(List<String> fields) {
        super(null);
        this.fields = fields;
    }

    @Override
    public String getMessage() {
        if (fields.size() == 1) {
            return "Mandatory parameter '" + fields.get(0) + "' not found";
        } else {
            return "Mandatory parameters '" + String.join(", ", fields) + "' not found";
        }
    }
}
