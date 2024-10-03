package com.walmartlabs.concord.config;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2018 Takari
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

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.typesafe.config.*;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.MethodParameterScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigModule extends AbstractModule {

    private static final String CONFIG_FILE = "concord.conf";
    private static final String LEGACY_CONFIG_FILE = "ollie.conf";

    private static final Provider<Object> NULL_PROVIDER = () -> null;

    private final com.typesafe.config.Config config;
    private final Reflections reflections;
    private final Set<Config> boundAnnotations;

    public ConfigModule(String packageToScan, com.typesafe.config.Config config) {
        var configBuilder = new ConfigurationBuilder()
                .filterInputsBy(new FilterBuilder().includePackage(packageToScan))
                .setUrls(ClasspathHelper.forPackage(packageToScan))
                .setScanners(
                        new TypeAnnotationsScanner(),
                        new MethodParameterScanner(),
                        new MethodAnnotationsScanner(),
                        new FieldAnnotationsScanner());

        this.config = config;
        this.reflections = new Reflections(configBuilder);
        this.boundAnnotations = new HashSet<>();
    }

    public static com.typesafe.config.Config load(String name) {
        var options = ConfigResolveOptions.defaults().setAllowUnresolved(true);
        var defaultConfig = ConfigFactory.load(name + ".conf", ConfigParseOptions.defaults(), options);
        var result = defaultConfig.getConfig(name);

        if (System.getProperty(LEGACY_CONFIG_FILE) != null) {
            var p = System.getProperty(LEGACY_CONFIG_FILE);
            var externalConfig = ConfigFactory.parseFile(new File(p)).getConfig(name);
            result = externalConfig.withFallback(result);
        }

        if (System.getProperty(CONFIG_FILE) != null) {
            var p = System.getProperty(CONFIG_FILE);
            var externalConfig = ConfigFactory.parseFile(new File(p)).getConfig(name);
            result = externalConfig.withFallback(result);
        }

        return result.resolve();
    }

    @Override
    public void configure() {
        var annotatedConstructors = reflections.getConstructorsWithAnyParamAnnotated(Config.class);
        for (var c : annotatedConstructors) {
            var params = c.getParameters();
            bindParameters(params);
        }

        var annotatedMethods = reflections.getMethodsWithAnyParamAnnotated(Config.class);
        for (var m : annotatedMethods) {
            var params = m.getParameters();
            bindParameters(params);
        }

        var annotatedFields = reflections.getFieldsAnnotatedWith(Config.class);
        for (var f : annotatedFields) {
            var annotation = f.getAnnotation(Config.class);
            bindValue(f.getType(), f.getAnnotatedType().getType(), annotation, isNullable(f.getAnnotations()));
        }
    }

    private void bindParameters(Parameter[] params) {
        for (var p : params) {
            if (!p.isAnnotationPresent(Config.class)) {
                continue;
            }

            var annotation = p.getAnnotation(Config.class);
            bindValue(p.getType(), p.getAnnotatedType().getType(), annotation, isNullable(p.getAnnotations()));
        }
    }

    private void bindValue(Class<?> paramClass, Type paramType, Config annotation, boolean nullable) {
        if (boundAnnotations.contains(annotation)) {
            return;
        }

        @SuppressWarnings("unchecked")
        var key = (Key<Object>) Key.get(paramType, annotation);

        var path = annotation.value();
        var value = getConfigValue(paramClass, paramType, path, nullable);

        if (value == null) {
            if (nullable) {
                bind(key).toProvider(NULL_PROVIDER);
            } else {
                throw new ConfigException.Missing(path);
            }
        } else {
            bind(key).toInstance(value);
        }

        boundAnnotations.add(annotation);
    }

    private Object getConfigValue(Class<?> paramClass, Type paramType, String path, boolean nullable) {
        var extractedValue = ConfigExtractors.extractConfigValue(config, paramClass, path);
        if (extractedValue.isPresent()) {
            return extractedValue.get();
        }

        if (nullable && !config.hasPath(path)) {
            return null;
        }

        var value = config.getValue(path);
        var type = value.valueType();
        if (type.equals(ConfigValueType.OBJECT) && Map.class.isAssignableFrom(paramClass)) {
            var object = config.getObject(path);
            return object.unwrapped();
        } else if (type.equals(ConfigValueType.OBJECT)) {
            return ConfigBeanFactory.create(config.getConfig(path), paramClass);
        } else if (type.equals(ConfigValueType.LIST) && List.class.isAssignableFrom(paramClass)) {
            var listType = ((ParameterizedType) paramType).getActualTypeArguments()[0];

            var extractedListValue = ListExtractors.extractConfigListValue(config, listType, path);

            if (extractedListValue.isPresent()) {
                return extractedListValue.get();
            } else {
                var configList = config.getConfigList(path);
                return configList.stream()
                        .map(cfg -> ConfigBeanFactory.create(cfg, (Class<?>) listType))
                        .collect(Collectors.toList());
            }
        }

        throw new RuntimeException("Cannot obtain config value for " + paramType + " at path: " + path);
    }

    private static boolean isNullable(Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return false;
        }

        return Arrays.stream(annotations)
                .anyMatch(a -> "Nullable".equals(a.annotationType().getSimpleName()));
    }
}
