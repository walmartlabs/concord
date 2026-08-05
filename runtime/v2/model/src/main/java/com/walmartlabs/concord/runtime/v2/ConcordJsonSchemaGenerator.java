package com.walmartlabs.concord.runtime.v2;

/*-
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaInject;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaString;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.schema.*;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

public class ConcordJsonSchemaGenerator {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    public static JsonNode generate() {
        Path baselineSchema = Paths.get("json_schema_previous.json");
        if (Files.exists(baselineSchema)) {
            try {
                return JSON_MAPPER.readTree(baselineSchema.toFile());
            } catch (IOException e) {
                throw new RuntimeException("Failed to read baseline JSON schema", e);
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JsonSchemaModule());

        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(objectMapper, SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule(JacksonOption.ALWAYS_REF_SUBTYPES, JacksonOption.RESPECT_JSONPROPERTY_REQUIRED));
        configBuilder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        configBuilder.with(Option.GETTER_METHODS, Option.NONSTATIC_NONVOID_NONGETTER_METHODS);
        configBuilder.forTypesInGeneral().withCustomDefinitionProvider(new TypeRemappingDefinitionProvider());
        configBuilder.forTypesInGeneral().withTypeAttributeOverride((node, scope, context) -> {
            applySchemaInject(node, findTypeAnnotation(objectMapper, scope, JsonSchemaInject.class));
            applySchemaTitle(node, findTypeAnnotation(objectMapper, scope, JsonSchemaTitle.class));
        });
        configBuilder.forFields().withInstanceAttributeOverride((node, field, context) -> {
            applySchemaInject(node, findMemberAnnotation(objectMapper, field, JsonSchemaInject.class));
            applySchemaTitle(node, findMemberAnnotation(objectMapper, field, JsonSchemaTitle.class));
        });
        configBuilder.forMethods().withInstanceAttributeOverride((node, method, context) -> {
            applySchemaInject(node, findMemberAnnotation(objectMapper, method, JsonSchemaInject.class));
            applySchemaTitle(node, findMemberAnnotation(objectMapper, method, JsonSchemaTitle.class));
        });
        configBuilder.forMethods().withIgnoreCheck(ConcordJsonSchemaGenerator::ignoreMethod);
        configBuilder.forMethods().withPropertyNameOverrideResolver(method -> methodPropertyName(objectMapper, method));

        SchemaGenerator schemaGenerator = new SchemaGenerator(configBuilder.build());
        JsonNode jsonSchema = schemaGenerator.generateSchema(ProcessDefinition.class);
        JsonNode definitions = definitionsNode(jsonSchema);

        // remove type attribute for entities with `@JsonTypeInfo`
        clearAllProperty(jsonSchema, "@type");
        clearProperty(path(definitions, "ImmutableMvnDefinition"), "type");
        clearProperty(path(definitions, "ImmutableGitDefinition"), "type");
        clearProperty(path(definitions, "ImmutableDirectoryDefinition"), "type");

        clearAllProperty(jsonSchema, "removeMe");

        // SwitchStep
        JsonNode switchDefault = path(definitions, "SwitchStep/properties/default");
        JsonNode switchStepNode = path(definitions, "SwitchStep");
        if (switchStepNode instanceof ObjectNode objectNode && !switchDefault.isMissingNode()) {
            objectNode.set("additionalProperties", switchDefault);
        }

        // remove invalid required primitive attributes
        removeRequired(path(definitions, "ProcessDefinitionConfiguration"), "debug", "parallelLoopParallelism");
        removeRequired(path(definitions, "EventConfiguration"), "recordEvents", "recordTaskInVars", "truncateInVars", "truncateMaxStringLength", "truncateMaxArrayLength", "truncateMaxDepth", "recordTaskOutVars", "truncateOutVars", "recordTaskMeta", "truncateMeta");
        removeRequired(path(definitions, "TaskCall"), "ignoreErrors");

        // remove invalid Object definition
        /*
            "additionalProperties" : {
              "$ref" : "#/definitions/Object"
            }
         */
        removeFieldIf(jsonSchema, "additionalProperties", n -> {
            String ref = n.path("$ref").asText();
            return "#/definitions/Object".equals(ref) || "#/$defs/Object".equals(ref);
        });
        normalizePropertyNames(jsonSchema);

        return jsonSchema;
    }

    public static void main(String[] args) throws Exception {
        JsonNode jsonSchema = generate();

        try (OutputStream os = outputStream(args)) {
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(os, jsonSchema);
        }
    }

    private static OutputStream outputStream(String[] args) throws IOException {
        if (args.length == 1) {
            Path schemaFile = Paths.get(args[0]);
            if (Files.notExists(schemaFile.getParent())) {
                Files.createDirectories(schemaFile.getParent());
            }
            return Files.newOutputStream(schemaFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            return System.out;
        }
    }

    private static JsonNode path(JsonNode root, String path) {
        JsonNode n = root;
        for (String p : path.split("/")) {
            n = n.path(p);
        }
        return n;
    }

    private static JsonNode definitionsNode(JsonNode schema) {
        JsonNode definitions = schema.path("definitions");
        if (!definitions.isMissingNode()) {
            return definitions;
        }
        return schema.path("$defs");
    }

    private static void applySchemaTitle(ObjectNode node, JsonSchemaTitle title) {
        if (title != null && !title.value().isBlank()) {
            node.put("title", title.value());
        }
    }

    private static void applySchemaInject(ObjectNode node, JsonSchemaInject inject) {
        if (inject == null) {
            return;
        }

        if (!inject.json().isBlank()) {
            JsonNode injectedNode;
            try {
                injectedNode = JSON_MAPPER.readTree(inject.json());
            } catch (IOException e) {
                throw new RuntimeException("Invalid @JsonSchemaInject json: " + inject.json(), e);
            }

            if (!(injectedNode instanceof ObjectNode)) {
                throw new IllegalStateException("@JsonSchemaInject json must be a JSON object");
            }

            if (!inject.merge()) {
                node.removeAll();
            }
            node.setAll((ObjectNode) injectedNode);
        }

        for (JsonSchemaString s : inject.strings()) {
            applyStringPath(node, s.path(), s.value());
        }
    }

    private static void applyStringPath(ObjectNode root, String path, String value) {
        String[] parts = path.split("/");
        ObjectNode current = root;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }

            if (i == parts.length - 1) {
                current.put(part, value);
                return;
            }

            JsonNode next = current.get(part);
            if (!(next instanceof ObjectNode)) {
                next = current.putObject(part);
            }
            current = (ObjectNode) next;
        }
    }

    private static <A extends Annotation> A findTypeAnnotation(ObjectMapper objectMapper, TypeScope scope, Class<A> annotationType) {
        BeanDescription bean = objectMapper.getSerializationConfig()
                .introspect(objectMapper.constructType(scope.getType().getErasedType()));
        return bean.getClassInfo().getAnnotation(annotationType);
    }

    private static <A extends Annotation> A findMemberAnnotation(ObjectMapper objectMapper, MemberScope<?, ?> scope, Class<A> annotationType) {
        BeanDescription bean = objectMapper.getSerializationConfig()
                .introspect(objectMapper.constructType(scope.getDeclaringType().getErasedType()));

        String schemaPropertyName = scope.getSchemaPropertyName();
        String declaredName = scope.getDeclaredName();
        String rawMemberName = scope.getRawMember().getName();

        for (BeanPropertyDefinition property : bean.findProperties()) {
            if (!matches(scope, property, schemaPropertyName, declaredName, rawMemberName)) {
                continue;
            }

            AnnotatedMember member = property.getPrimaryMember();
            if (member == null) {
                continue;
            }

            A annotation = member.getAnnotation(annotationType);
            if (annotation != null) {
                return annotation;
            }
        }

        return null;
    }

    private static boolean matches(MemberScope<?, ?> scope, BeanPropertyDefinition property, String schemaPropertyName, String declaredName, String rawMemberName) {
        if (schemaPropertyName != null && schemaPropertyName.equals(property.getName())) {
            return true;
        }

        if (declaredName != null && declaredName.equals(property.getInternalName())) {
            return true;
        }

        AnnotatedMember primary = property.getPrimaryMember();
        if (primary == null) {
            return false;
        }

        return rawMemberName.equals(primary.getName()) || scope.getName().equals(property.getName());
    }

    private static boolean ignoreMethod(MethodScope method) {
        Method rawMethod = method.getRawMember();
        if (rawMethod.getDeclaringClass() == Object.class) {
            return true;
        }

        if (rawMethod.getParameterCount() > 0) {
            return true;
        }

        Package declaringPackage = rawMethod.getDeclaringClass().getPackage();
        if (declaringPackage != null && declaringPackage.getName().startsWith("java.")) {
            return true;
        }

        String name = rawMethod.getName();
        return "toString".equals(name) || "hashCode".equals(name) || "equals".equals(name);
    }

    private static String methodPropertyName(ObjectMapper objectMapper, MethodScope method) {
        JsonProperty jsonProperty = findMemberAnnotation(objectMapper, method, JsonProperty.class);
        if (jsonProperty != null && !jsonProperty.value().isBlank()) {
            return jsonProperty.value();
        }

        String name = method.getDeclaredName();
        if (name.endsWith("()")) {
            return name.substring(0, name.length() - 2);
        }
        return name;
    }

    private static void removeProperty(JsonNode node, String propName) {
        JsonNode propsNode = node.path("properties");
        if (propsNode instanceof ObjectNode objectNode) {
            objectNode.remove(propName);
        }
    }

    private static void removeRequired(JsonNode node, String... fieldNames) {
        JsonNode requiredNode = node.path("required");
        if (requiredNode.isMissingNode()) {
            return;
        }

        for (Iterator<JsonNode> it = requiredNode.elements(); it.hasNext(); ) {
            JsonNode n = it.next();
            if (Arrays.stream(fieldNames).anyMatch(f -> f.equals(n.asText()))) {
                it.remove();
            }
        }
        if (!requiredNode.elements().hasNext()) {
            ((ObjectNode) node).remove("required");
        }
    }

    private static void clearProperty(JsonNode node, String propName) {
        removeProperty(node, propName);
        removeRequired(node, propName);
    }

    private static void clearAllProperty(JsonNode node, String propName) {
        for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
            JsonNode n = it.next();
            if (n instanceof ObjectNode) {
                clearProperty(n, propName);
                clearAllProperty(n, propName);
            }
        }
    }

    private static void normalizePropertyNames(JsonNode root) {
        if (!(root instanceof ObjectNode objectNode)) {
            return;
        }

        JsonNode propertiesNode = objectNode.get("properties");
        if (propertiesNode instanceof ObjectNode propertiesObject) {
            List<String> fieldNames = new ArrayList<>();
            propertiesObject.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                String normalized = normalizedPropertyName(fieldName);
                if (!normalized.equals(fieldName)) {
                    JsonNode value = propertiesObject.remove(fieldName);
                    propertiesObject.set(normalized, value);
                }
            }
        }

        for (Iterator<JsonNode> it = objectNode.elements(); it.hasNext(); ) {
            normalizePropertyNames(it.next());
        }
    }

    private static String normalizedPropertyName(String fieldName) {
        if (fieldName.endsWith("()")) {
            return fieldName.substring(0, fieldName.length() - 2);
        }
        return fieldName;
    }

    private static void removeFieldIf(JsonNode root, String fieldName, Predicate<JsonNode> p) {
        for (Iterator<JsonNode> it = root.elements(); it.hasNext(); ) {
            JsonNode n = it.next();
            JsonNode ap = n.path(fieldName);
            if (!ap.isMissingNode() && p.test(ap)) {
                ((ObjectNode) n).remove(fieldName);
            } else {
                removeFieldIf(n, fieldName, p);
            }
        }
    }

    private static class JsonSchemaModule extends SimpleModule {

        private static final long serialVersionUID = 1L;

        public JsonSchemaModule() {
            setMixInAnnotation(ProcessDefinition.class, ProcessDefinitionMixIn.class);
            setMixInAnnotation(ProcessDefinitionConfiguration.class, ProcessDefinitionConfigurationMixIn.class);
            setMixInAnnotation(Trigger.class, TriggerMixIn.class);
            setMixInAnnotation(Step.class, StepMixIn.class);

            addSerializer(Duration.class, new StdSerializer<>(Duration.class) {
                private static final long serialVersionUID = 1L;

                @Override
                public void serialize(Duration value, JsonGenerator gen, SerializerProvider provider) {
                    // do nothing
                }

                @Override
                public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
                    visitor.expectStringFormat(typeHint);
                }
            });
        }

    }

    private static class TypeRemappingDefinitionProvider implements CustomDefinitionProviderV2 {

        @Override
        public CustomDefinition provideCustomSchemaDefinition(ResolvedType javaType, SchemaGenerationContext context) {
            Class<?> erasedType = javaType.getErasedType();

            if (Form.class.isAssignableFrom(erasedType)) {
                ObjectNode node = JSON_MAPPER.createObjectNode();
                node.put("type", "object");
                return new CustomDefinition(node);
            }

            Class<?> mappedType = null;
            if (Imports.class.isAssignableFrom(erasedType)) {
                mappedType = ImportsMixIn.class;
            } else if (Flow.class.isAssignableFrom(erasedType)) {
                mappedType = FlowsMixIn.class;
            }

            if (mappedType == null) {
                return null;
            }

            ResolvedType remappedType = context.getTypeContext().resolve(mappedType);
            ObjectNode definitionRef = context.createStandardDefinitionReference(remappedType, this);
            return new CustomDefinition(definitionRef, CustomDefinition.INLINE_DEFINITION, CustomDefinition.EXCLUDING_ATTRIBUTES);
        }
    }
}
