package com.walmartlabs.concord.server.sdk.validation;

/*
 * Copyright (c) 2007-2014 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 *
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Based on the original {@link org.sonatype.siesta.server.validation.ValidationErrorsException}.
 */
public class ValidationErrorsException extends RuntimeException {
    private final List<ValidationErrorXO> errors = new ArrayList<ValidationErrorXO>();

    public ValidationErrorsException() {
        super();
    }

    public ValidationErrorsException(final String message) {
        errors.add(new ValidationErrorXO(message));
    }

    public ValidationErrorsException(final String id, final String message) {
        errors.add(new ValidationErrorXO(id, message));
    }

    public ValidationErrorsException withError(final String message) {
        errors.add(new ValidationErrorXO(message));
        return this;
    }

    public ValidationErrorsException withError(final String id, final String message) {
        errors.add(new ValidationErrorXO(id, message));
        return this;
    }

    public ValidationErrorsException withErrors(final ValidationErrorXO... validationErrors) {
        errors.addAll(Arrays.asList(requireNonNull(validationErrors)));
        return this;
    }

    public ValidationErrorsException withErrors(final List<ValidationErrorXO> validationErrors) {
        errors.addAll(requireNonNull(validationErrors));
        return this;
    }

    public List<ValidationErrorXO> getValidationErrors() {
        return errors;
    }

    public boolean hasValidationErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        for (ValidationErrorXO error : errors) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(error.getMessage());
        }
        return sb.isEmpty() ? "(No validation errors)" : sb.toString();
    }
}
