package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.exception.InvalidFieldDefinitionException;
import com.walmartlabs.concord.runtime.v2.exception.YamlParserException;
import com.walmartlabs.concord.runtime.v2.exception.YamlProcessingException;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class YamlParserV2 {

    private final ObjectMapper objectMapper;

    public YamlParserV2() {
        ObjectMapper om = new ObjectMapper(new YAMLFactory()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
                .disable(MapperFeature.USE_ANNOTATIONS);

        SimpleModule module = new SimpleModule();
        module.addDeserializer(ProcessDefinition.class, YamlDeserializersV2.getProcessDefinitionDeserializer());

        om.registerModule(module);
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());

        this.objectMapper = om;
    }

    public ProcessDefinition parse(Path baseDir, Path file) throws IOException {
        String fileName = baseDir.relativize(file).toString();
        try {
            return ThreadLocalFileName.withFileName(fileName,
                    () -> objectMapper.readValue(file.toFile(), ProcessDefinition.class));
        } catch (YamlProcessingException e) {
            throw new YamlParserException(buildErrorMessage(fileName, e));
        } catch (Exception e) {
            if (e.getCause() instanceof JsonProcessingException) {
                JsonProcessingException jpe = (JsonProcessingException) e.getCause();
                throw toErr("(" + fileName + "): Error", jpe);
            }
            throw new YamlParserException("Error while loading a project file '" + baseDir.relativize(file) +"', " + e.getMessage());
        }
    }

    private static YamlParserException toErr(String msg, JsonProcessingException jpe) {
        String loc = toShortString(jpe.getLocation());
        String originalMsg = jpe.getOriginalMessage();
        return new YamlParserException(msg + " @ " + loc + ". " + originalMsg);
    }

    private static String buildErrorMessage(String fileName, YamlProcessingException e) {
        String prefix = "(" + fileName + "): Error";

        List<YamlProcessingException> errors = getYamlProcessingExceptionList(e);
        Collections.reverse(errors);

        String padding = "\t";
        StringBuilder result = new StringBuilder(toMessage(prefix, errors.remove(0)));

        List<InvalidFieldDefinitionException> stepErrors = errors.stream().filter(err -> err instanceof InvalidFieldDefinitionException)
                .map(err -> (InvalidFieldDefinitionException) err).collect(Collectors.toList());
        if (stepErrors.size() > 0) {
            result.append("\n\t").append("while processing steps:");
        }
        for (InvalidFieldDefinitionException err : stepErrors) {
            result.append("\n").append(padding)
                    .append("'").append(err.getFieldName()).append("'")
                    .append(" @ ").append(Location.toShortString(err.getLocation()));
            padding += "\t";
        }
        return result.toString();
    }

    private static List<YamlProcessingException> getYamlProcessingExceptionList(YamlProcessingException e) {
        List<YamlProcessingException> list = new ArrayList<>();
        while (e != null && !list.contains(e)) {
            list.add(e);
            Throwable cause = e.getCause();
            if (cause instanceof YamlProcessingException) {
                e = (YamlProcessingException) cause;
            } else {
                e = null;
            }
        }
        return list;
    }

    private static String toMessage(String prefix, YamlProcessingException e) {
        return prefix + " @ " + Location.toShortString(e.getLocation()) + ". " + e.getMessage();
    }

    public static String toShortString(JsonLocation location) {
        if (location == null) {
            return "n/a";
        }
        return "line: " + location.getLineNr() + ", col: " + location.getColumnNr();
    }

}
