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
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public enum ListExtractors implements ListExtractor {
    BOOLEAN(Boolean.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getBooleanList(path);
        }
    },
    INTEGER(Integer.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getIntList(path);
        }
    },
    DOUBLE(Double.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getDoubleList(path);
        }
    },
    LONG(Long.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getLongList(path);
        }
    },
    STRING(String.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getStringList(path);
        }
    },
    DURATION(Duration.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getDurationList(path);
        }
    },
    MEMORY_SIZE(ConfigMemorySize.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getMemorySizeList(path);
        }
    },
    OBJECT(Object.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getAnyRefList(path);
        }
    },
    CONFIG(Config.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getConfigList(path);
        }
    },
    CONFIG_OBJECT(ConfigObject.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getObjectList(path);
        }
    },
    CONFIG_VALUE(ConfigObject.class) {
        @Override
        public List<?> extractListValue(Config config, String path) {
            return config.getList(path);
        }
    };

    private final Class<?> parameterizedTypeClass;
    private static final Map<Type, ListExtractor> EXTRACTOR_MAP = new HashMap<>();

    static {
        for (var extractor : ListExtractors.values()) {
            EXTRACTOR_MAP.put(extractor.getMatchingParameterizedType(), extractor);
        }
    }

    ListExtractors(Class<?> parameterizedTypeClass) {
        this.parameterizedTypeClass = parameterizedTypeClass;
    }

    @Override
    public Type getMatchingParameterizedType() {
        return parameterizedTypeClass;
    }

    static Optional<List<?>> extractConfigListValue(Config config, Type listType, String path) {
        if (EXTRACTOR_MAP.containsKey(listType)) {
            return Optional.of(EXTRACTOR_MAP.get(listType).extractListValue(config, path));
        } else {
            return Optional.empty();
        }
    }
}
