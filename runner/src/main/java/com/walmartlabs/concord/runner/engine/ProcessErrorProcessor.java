package com.walmartlabs.concord.runner.engine;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.sdk.Constants;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.ExecutionException;

import javax.el.ELException;
import java.util.HashMap;
import java.util.Map;

public class ProcessErrorProcessor {

    private static final String DEFAULT_ERROR_REF = "__default_error_ref";

    private static final ObjectMapper mapper = createMapper();

    public static Map<String, Object> process(Throwable t) {
        Map<String, Object> m = new HashMap<>();
        m.put(Constants.Context.LAST_ERROR_KEY, mapper.convertValue(getError(t), Map.class));
        return m;
    }

    private static Throwable getError(Throwable t) {
        t = unroll(t);

        if (t instanceof BpmnError) {
            BpmnError error = (BpmnError) t;
            if (!DEFAULT_ERROR_REF.equals(error.getErrorRef())) {
                return error;
            }

            Throwable cause = error.getCause();
            if (cause == null) {
                return error;
            }

            if (cause instanceof ELException) {
                return cause.getCause();
            }

            return cause;
        }

        return t;
    }

    private static Throwable unroll(Throwable t) {
        if (t instanceof ExecutionException) {
            if (t.getCause() != null) {
                return t.getCause();
            }
        }
        return t;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.addMixIn(Throwable.class, ExceptionMixIn.class);
        om.addMixIn(BpmnError.class, BpmnErrorMixIn.class);
        return om;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract class ExceptionMixIn {
        @JsonIgnore
        abstract StackTraceElement[] getStackTrace();

        @JsonIgnore
        abstract String getLocalizedMessage();

        @JsonIgnore
        abstract Throwable[] getSuppressed();

        @JsonIgnore
        abstract Throwable getCause();
    }

    abstract class BpmnErrorMixIn {
        @JsonIgnore
        abstract String getDefinitionId();

        @JsonIgnore
        abstract String getElementId();

        @JsonProperty("message")
        abstract String getErrorRef();

        @JsonIgnore
        abstract String getMessage();
    }
}
