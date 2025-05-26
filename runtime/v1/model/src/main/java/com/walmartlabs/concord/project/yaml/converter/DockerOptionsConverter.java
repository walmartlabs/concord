package com.walmartlabs.concord.project.yaml.converter;

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

import com.walmartlabs.concord.common.DockerProcessBuilder;
import io.takari.parc.Seq;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Keeping it for backward compatibility with older version of ansible-plugin.
 */
@Deprecated
public class DockerOptionsConverter {

    public static List<Map.Entry<String, String>> convert(Map<String, Object> options) {
        if (options == null) {
            return Collections.emptyList();
        }

        DockerProcessBuilder.DockerOptionsBuilder result = new DockerProcessBuilder.DockerOptionsBuilder();

        getList(options, "hosts").forEach(result::etcHost);

        return result.build();
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getList(Map<String, Object> options, String key) {
        Object o = options.get(key);
        if (o == null) {
            return Collections.emptyList();
        }

        if (o instanceof Collection) {
            return (Collection<String>) o;
        }

        if (o instanceof Seq) {
            return ((Seq) o).toList();
        }

        throw new IllegalArgumentException("unexpected '" + key + "' type: " + o);
    }
}
