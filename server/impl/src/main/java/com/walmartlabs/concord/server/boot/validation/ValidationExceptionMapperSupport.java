package com.walmartlabs.concord.server.boot.validation;

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
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import org.sonatype.siesta.ExceptionMapperSupport;
import org.sonatype.siesta.ValidationErrorXO;

import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.util.List;

import static org.sonatype.siesta.MediaTypes.VND_VALIDATION_ERRORS_V1_JSON_TYPE;
import static org.sonatype.siesta.MediaTypes.VND_VALIDATION_ERRORS_V1_XML_TYPE;

/**
 * Based on the original {@link org.sonatype.siesta.server.validation.ValidationExceptionMapperSupport}.
 */
public abstract class ValidationExceptionMapperSupport<E extends Throwable> extends ExceptionMapperSupport<E> {

    private final List<Variant> variants;

    public ValidationExceptionMapperSupport() {
        this.variants = Variant.mediaTypes(
                VND_VALIDATION_ERRORS_V1_JSON_TYPE,
                VND_VALIDATION_ERRORS_V1_XML_TYPE
        ).add().build();
    }

    @Override
    protected Response convert(final E exception, final String id) {
        final Response.ResponseBuilder builder = Response.status(getStatus(exception));

        final List<ValidationErrorXO> errors = getValidationErrors(exception);
        if (errors != null && !errors.isEmpty()) {
            final Variant variant = getRequest().selectVariant(variants);
            if (variant != null) {
                builder.type(variant.getMediaType())
                        .entity(
                                new GenericEntity<List<ValidationErrorXO>>(errors) {
                                    @Override
                                    public final String toString() {
                                        return getEntity().toString();
                                    }
                                }
                        );
            }
        }

        return builder.build();
    }

    protected Status getStatus(final E exception) {
        return Status.BAD_REQUEST;
    }

    protected abstract List<ValidationErrorXO> getValidationErrors(final E exception);

    private Request request;

    @Context
    public void setRequest(final Request request) {
        if (request == null) {
            throw new NullPointerException();
        }
        this.request = request;
    }

    protected Request getRequest() {
        if (request == null) {
            throw new IllegalStateException();
        }
        return request;
    }
}
