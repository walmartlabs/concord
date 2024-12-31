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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.walmartlabs.concord.runtime.v2.runner.PersistenceService;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Saves the current unhandled exception as a process metadata variable.
 */
public class SaveLastErrorCommand implements Command {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = 5759484819869224819L;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final AtomicInteger idGenerator = new AtomicInteger(1);

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
        var module = new SimpleModule();
        module.addSerializer(ParallelExecutionException.class, new ParallelExceptionSerializer());
        module.addSerializer(LoggedException.class, new LoggedExceptionSerializer());
        module.addSerializer(UserDefinedException.class, new UserDefinedExceptionSerializer());
        module.addSerializer(Exception.class, new ExceptionSerializer());

        var om = new ObjectMapper();
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.registerModule(module);
        return om;
    }

    private static class ParallelExceptionSerializer extends JsonSerializer<ParallelExecutionException> {

        @Override
        public void serialize(ParallelExecutionException exception, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("@id", idGenerator.getAndIncrement());
            gen.writeStringField("message", exception.getMessage());

            gen.writeArrayFieldStart("exceptions");
            for (var e : exception.getExceptions()) {
                gen.writeObject(e);
            }
            gen.writeEndArray();

            gen.writeEndObject();
        }
    }

    private static class LoggedExceptionSerializer extends JsonSerializer<LoggedException> {

        @Override
        public void serialize(LoggedException exception, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeObject(exception.getCause());
        }
    }

    private static class UserDefinedExceptionSerializer extends JsonSerializer<UserDefinedException> {

        @Override
        public void serialize(UserDefinedException exception, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("@id", idGenerator.getAndIncrement());
            gen.writeStringField("message", exception.getMessage());
            if (exception.getPayload() != null && !exception.getPayload().isEmpty()) {
                gen.writeObjectField("payload", exception.getPayload());
            }
            gen.writeEndObject();
        }
    }

    private static class ExceptionSerializer extends JsonSerializer<Exception> {

        @Override
        public void serialize(Exception exception, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("@id", idGenerator.getAndIncrement());
            gen.writeStringField("message", exception.getMessage());
            gen.writeStringField("type", exception.getClass().getName());
            gen.writeEndObject();
        }
    }
}
