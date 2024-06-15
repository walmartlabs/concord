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
package com.walmartlabs.concord.server.boot.validation;

import com.walmartlabs.concord.server.sdk.validation.ValidationErrorXO;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ElementKind;
import javax.validation.Path;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Based on the original {@link org.sonatype.siesta.server.validation.ConstraintViolationExceptionMapper}.
 */
@Provider
public class ConstraintViolationExceptionMapper extends ValidationExceptionMapperSupport<ConstraintViolationException> {

    @Override
    protected List<ValidationErrorXO> getValidationErrors(final ConstraintViolationException exception) {
        return getValidationErrors(exception.getConstraintViolations());
    }

    @Override
    protected Status getStatus(final ConstraintViolationException exception) {
        return getResponseStatus(exception.getConstraintViolations());
    }

    private List<ValidationErrorXO> getValidationErrors(Set<ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(v -> new ValidationErrorXO(getPath(v), v.getMessage()))
                .toList();
    }

    private Status getResponseStatus(Set<ConstraintViolation<?>> violations) {
        Iterator<ConstraintViolation<?>> iterator = violations.iterator();

        if (iterator.hasNext()) {
            return getResponseStatus(iterator.next());
        } else {
            return Status.BAD_REQUEST;
        }
    }

    private Status getResponseStatus(ConstraintViolation<?> violation) {
        for (Path.Node node : violation.getPropertyPath()) {
            ElementKind kind = node.getKind();

            if (ElementKind.RETURN_VALUE.equals(kind)) {
                return Status.INTERNAL_SERVER_ERROR;
            }
        }

        return Status.BAD_REQUEST;
    }

    private String getPath(ConstraintViolation<?> violation) {
        String leafBeanName = violation.getLeafBean().getClass().getSimpleName();
        int proxySuffix = leafBeanName.indexOf("$$EnhancerByGuice");
        if (proxySuffix > 0) {
            leafBeanName = leafBeanName.substring(0, proxySuffix);
        }

        String propertyPath = violation.getPropertyPath().toString();

        return leafBeanName + (!"".equals(propertyPath) ? '.' + propertyPath : "");
    }
}
