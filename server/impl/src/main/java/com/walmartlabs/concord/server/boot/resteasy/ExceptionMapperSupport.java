package com.walmartlabs.concord.server.boot.resteasy;

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

import com.walmartlabs.concord.server.sdk.rest.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Based on the original {@link org.sonatype.siesta.ExceptionMapperSupport}.
 */
public abstract class ExceptionMapperSupport<E extends Throwable> implements ExceptionMapper<E>, Component {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public Response toResponse(E exception) {
        if (exception == null) {
            throw new NullPointerException();
        }

        // debug/trace log exception details
        if (log.isTraceEnabled()) {
            log.trace("Mapping exception: " + exception, exception);
        } else {
            log.debug("Mapping exception: " + exception);
        }

        // Prepare the response
        Response response;
        try {
            response = convert(exception);
        } catch (Exception e) {
            log.warn("Failed to map exception", e);
            response = Response.serverError().entity(e.getMessage()).build();
        }

        // Log terse (unless debug enabled) warning with fault details
        Object entity = response.getEntity();
        log.warn("Response: [{}] {}; mapped from: {}",
                response.getStatus(),
                entity == null ? "(no entity/body)" : String.format("'%s'", entity),
                exception,
                log.isDebugEnabled() ? exception : null
        );

        return response;
    }

    /**
     * Convert the given exception into a response.
     *
     * @param exception The exception to convert.
     */
    protected abstract Response convert(E exception);
}
