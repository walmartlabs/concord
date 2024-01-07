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

import com.walmartlabs.concord.server.boot.resteasy.ExceptionMapperSupport;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorXO;

import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Based on the original {@link org.sonatype.siesta.server.validation.ValidationExceptionMapperSupport}.
 */
public abstract class ValidationExceptionMapperSupport<E extends Throwable> extends ExceptionMapperSupport<E> {

    private final List<Variant> variants;

    public ValidationExceptionMapperSupport() {
        this.variants = Variant.mediaTypes(
                new MediaType("application", "vnd.concord-validation-errors-v1+json")
        ).add().build();
    }

    @Override
    protected Response convert(E exception) {
        ResponseBuilder builder = Response.status(getStatus(exception));

        List<ValidationErrorXO> errors = getValidationErrors(exception);
        if (errors != null && !errors.isEmpty()) {
            Variant variant = getRequest().selectVariant(variants);
            if (variant != null) {
                builder.type(variant.getMediaType())
                        .entity(
                                new GenericEntity<>(errors) {
                                    @Override
                                    public String toString() {
                                        return getEntity().toString();
                                    }
                                }
                        );
            }
        }

        return builder.build();
    }

    protected Status getStatus(E exception) {
        return Status.BAD_REQUEST;
    }

    protected abstract List<ValidationErrorXO> getValidationErrors(E exception);

    private Request request;

    @Context
    public void setRequest(Request request) {
        this.request = requireNonNull(request);
    }

    protected Request getRequest() {
        return requireNonNull(request);
    }
}
