package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.runtime.v2.runner.PersistenceService;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Saves the current unhandled exception as a process metadata variable.
 */
public class SaveLastErrorCommand implements Command {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = 5759484819869224819L;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) throws Exception {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        Exception e = VMUtils.assertLocal(state, threadId, Frame.LAST_EXCEPTION_KEY);

        ObjectMapper om = runtime.getService(ObjectMapper.class);
        PersistenceService persistenceService = runtime.getService(PersistenceService.class);
        Map<String, Object> currentOut = persistenceService.loadPersistedFile(Constants.Files.OUT_VALUES_FILE_NAME, in -> om.readValue(in, MAP_TYPE));

        Map<String, Object> outValues = new HashMap<>(currentOut != null ? currentOut : Collections.emptyMap());
        outValues.put(Constants.Context.LAST_ERROR_KEY, serialize(e));
        persistenceService.persistFile(Constants.Files.OUT_VALUES_FILE_NAME,
                out -> om.writeValue(out, outValues));

        throw e;
    }

    private static Map<String, Object> serialize(Exception e) {
        try {
            return createMapper().convertValue(e, MAP_TYPE);
        } catch (Exception ex) {
            // ignore ex
            return Collections.singletonMap("message", e.getMessage());
        }
    }

    private static ObjectMapper createMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.addMixIn(Throwable.class, ExceptionMixIn.class);
        return om;
    }

    @SuppressWarnings("unused")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIdentityInfo(generator= ObjectIdGenerators.IntSequenceGenerator.class)
    abstract static class ExceptionMixIn {
        @JsonIgnore
        abstract StackTraceElement[] getStackTrace();

        @JsonIgnore
        abstract String getLocalizedMessage();

        @JsonIgnore
        abstract Throwable[] getSuppressed();

        @JsonIgnore
        abstract Throwable getCause();
    }
}
