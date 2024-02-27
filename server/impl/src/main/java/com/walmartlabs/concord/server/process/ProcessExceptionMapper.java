package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.server.sdk.PartialProcessKey;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

@Named
@Singleton
@Provider
public class ProcessExceptionMapper extends ExceptionMapperSupport<ProcessException> {

    private static final int MAX_CAUSE_DEPTH = 5;

    public static final String TRACE_ENABLED_KEY = "X-Concord-Trace-Enabled";

    @Context
    HttpHeaders headers;

    @Override
    protected Response convert(ProcessException e) {
        String details = getDetails(e.getCause());

        String stacktrace = null;
        if (traceEnabled()) {
            StringWriter w = new StringWriter();
            e.printStackTrace(new PrintWriter(w));
            stacktrace = w.toString();
        }

        PartialProcessKey processKey = e.getProcessKey();
        UUID instanceId = processKey.getInstanceId();

        ErrorMessage msg = new ErrorMessage(instanceId, e.getMessage(), details, stacktrace);
        return Response.status(e.getStatus())
                .entity(msg)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    private boolean traceEnabled() {
        String s = headers.getHeaderString(TRACE_ENABLED_KEY);
        return Boolean.parseBoolean(s);
    }

    private static String getDetails(Throwable t) {
        if (t == null) {
            return null;
        }

        int currentDepth = 0;
        StringBuilder result = new StringBuilder();
        while (t != null && currentDepth < MAX_CAUSE_DEPTH) {
            result.append(t.getMessage()).append("\n");
            t = t.getCause();
            currentDepth++;
        }
        return result.toString();
    }
}
