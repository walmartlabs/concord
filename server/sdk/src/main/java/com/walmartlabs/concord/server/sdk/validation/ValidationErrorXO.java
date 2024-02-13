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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Based on the original {@link org.sonatype.siesta.ValidationErrorsXO}.
 */
public class ValidationErrorXO {
    /**
     * Denotes that validation does not applies to a specific value.
     */
    public static final String GENERIC = "*";

    /**
     * Identifies the value value that is failing validation. A value of "*" denotes that validation
     * does not applies to a specific value.
     * <p>
     * E.g. "name".
     */
    @JsonProperty
    private String id;

    /**
     * Description of failing validation.
     * <p>
     * E.g. "Name cannot be null".
     */
    @JsonProperty
    private String message;

    public ValidationErrorXO() {
        this.id = GENERIC;
    }

    /**
     * Creates a validation error that does not applies to a specific value.
     *
     * @param message validation description
     */
    public ValidationErrorXO(String message) {
        this(GENERIC, message);
    }

    /**
     * Creates a validation error for a specific value.
     *
     * @param id      identifier of value failing validation.
     * @param message validation description
     */
    public ValidationErrorXO(String id, final String message) {
        this.id = id == null ? GENERIC : id;
        this.message = message;
    }

    /**
     * @return identifier of value failing validation (never null).  A value of "*" denotes that validation does
     * not applies to a specific value.
     */
    public String getId() {
        return id;
    }

    /**
     * @param id of value failing validation
     */
    public void setId(String id) {
        this.id = id == null ? GENERIC : id;
    }

    /**
     * @param id of value failing validation
     * @return itself, for fluent api usage
     */
    public ValidationErrorXO withId(String id) {
        setId(id);
        return this;
    }

    /**
     * @return validation description
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param message validation description
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * @param message validation description
     * @return itself, for fluent api usage
     */
    public ValidationErrorXO withMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
               "id='" + id + '\'' +
               ", message='" + message + '\'' +
               '}';
    }
}
