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

import com.walmartlabs.concord.runtime.model.Location;

public class InvalidFieldDefinitionException extends YamlProcessingException {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = 7839061025127853584L;

    private final String fieldName;
    private final YamlProcessingException cause;

    public InvalidFieldDefinitionException(String fieldName, Location stepLocation, YamlProcessingException cause) {
        super(stepLocation);
        this.fieldName = fieldName;
        this.cause = cause;
    }

    @Override
    public String getMessage() {
        return "Invalid '" + fieldName + "' definition";
    }

    @Override
    public synchronized YamlProcessingException getCause() {
        return cause;
    }

    public String getFieldName() {
        return fieldName;
    }
}
