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

import com.typesafe.config.Config;
import com.typesafe.config.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

enum ConfigExtractors implements ConfigExtractor {

    BOOLEAN(boolean.class, Boolean.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getBoolean(path);
        }
    },
    BYTE(byte.class, Byte.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return (byte) config.getInt(path);
        }
    },
    SHORT(short.class, Short.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return (short) config.getInt(path);
        }
    },
    INTEGER(int.class, Integer.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getInt(path);
        }
    },
    LONG(long.class, Long.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getLong(path);
        }
    },
    FLOAT(float.class, Float.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return (float) config.getDouble(path);
        }
    },
    DOUBLE(double.class, Double.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getDouble(path);
        }
    },
    STRING(String.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getString(path);
        }
    },
    PATH(Path.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return Paths.get(config.getString(path));
        }
    },
    ANY_REF(Object.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getAnyRef(path);
        }
    },
    CONFIG(Config.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getConfig(path);
        }
    },
    CONFIG_OBJECT(ConfigObject.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getObject(path);
        }
    },
    CONFIG_VALUE(ConfigValue.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getValue(path);
        }
    },
    CONFIG_LIST(ConfigList.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getList(path);
        }
    },
    DURATION(Duration.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getDuration(path);
        }
    },
    MEMORY_SIZE(ConfigMemorySize.class) {
        @Override
        public Object extractValue(Config config, String path) {
            return config.getMemorySize(path);
        }
    },
    BYTE_ARRAY(byte[].class) {
        @Override
        public Object extractValue(Config config, String path) {
            return Base64.getDecoder().decode(config.getString(path));
        }
    };

    private final Class<?>[] matchingClasses;
    private static final Map<Class<?>, ConfigExtractor> EXTRACTOR_MAP = new HashMap<>();

    static {
        for (var extractor : ConfigExtractors.values()) {
            for (var clazz : extractor.getMatchingClasses()) {
                EXTRACTOR_MAP.put(clazz, extractor);
            }
        }
    }

    ConfigExtractors(Class<?>... matchingClasses) {
        this.matchingClasses = matchingClasses;
    }

    @Override
    public Class<?>[] getMatchingClasses() {
        return matchingClasses;
    }

    static Optional<Object> extractConfigValue(Config config, Class<?> paramClass, String path) {
        if (config.hasPath(path) && EXTRACTOR_MAP.containsKey(paramClass)) {
            return Optional.of(EXTRACTOR_MAP.get(paramClass).extractValue(config, path));
        } else {
            return Optional.empty();
        }
    }
}
