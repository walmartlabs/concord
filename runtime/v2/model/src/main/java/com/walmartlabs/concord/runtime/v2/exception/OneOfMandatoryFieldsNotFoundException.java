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

import java.util.Arrays;
import java.util.List;

public class OneOfMandatoryFieldsNotFoundException extends YamlProcessingException {

    private static final long serialVersionUID = 1L;

    private final List<String> fields;

    public OneOfMandatoryFieldsNotFoundException(String... fields) {
        this(Arrays.asList(fields));
    }

    public OneOfMandatoryFieldsNotFoundException(List<String> fields) {
        super(null);
        this.fields = fields;
    }

    @Override
    public String getMessage() {
        return "One of mandatory parameters '" + String.join(", ", fields) + "' not found";
    }
}
