package com.walmartlabs.concord.build.shade;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseNoticeResourceTransformerTest {

    @Test
    void recognizesLicenseAndNoticeVariants() {
        var transformer = new LicenseNoticeResourceTransformer();

        assertTrue(transformer.canTransformResource("META-INF/LICENSE"));
        assertTrue(transformer.canTransformResource("meta-inf/license.TXT"));
        assertTrue(transformer.canTransformResource("META-INF/LICENSE.md"));
        assertTrue(transformer.canTransformResource("META-INF/NOTICE"));
        assertTrue(transformer.canTransformResource("meta-inf/notice.TXT"));
        assertTrue(transformer.canTransformResource("META-INF/NOTICE.md"));
        assertFalse(transformer.canTransformResource("META-INF/LICENSE.html"));
        assertFalse(transformer.canTransformResource("META-INF/DEPENDENCIES"));
    }

    @Test
    void canonicalizesAndDeduplicatesResources() throws Exception {
        var transformer = new LicenseNoticeResourceTransformer();

        transformer.processResource("META-INF/LICENSE.txt", resource("Apache license"), List.of(), 2_000);
        transformer.processResource("META-INF/LICENSE", resource("Apache license"), List.of(), 4_000);
        transformer.processResource("META-INF/LICENSE.md", resource("Other license\n"), List.of(), 6_000);
        transformer.processResource("META-INF/NOTICE.txt", resource("First notice"), List.of(), 8_000);
        transformer.processResource("META-INF/NOTICE.md", resource("Second notice\n"), List.of(), 10_000);

        var result = result(transformer);

        assertEquals(List.of("META-INF/LICENSE", "META-INF/NOTICE"), List.copyOf(result.keySet()));
        assertEquals("Apache license\n\nOther license\n", result.get("META-INF/LICENSE").contents());
        assertEquals(6_000, result.get("META-INF/LICENSE").time());
        assertEquals("First notice\n\nSecond notice\n", result.get("META-INF/NOTICE").contents());
        assertEquals(10_000, result.get("META-INF/NOTICE").time());
    }

    private static ByteArrayInputStream resource(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Resource> result(LicenseNoticeResourceTransformer transformer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            transformer.modifyOutputStream(jar);
        }

        var result = new LinkedHashMap<String, Resource>();
        try (var jar = new JarInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            java.util.jar.JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                result.put(entry.getName(), new Resource(new String(jar.readAllBytes(), StandardCharsets.UTF_8), entry.getTime()));
            }
        }
        return result;
    }

    private record Resource(String contents, long time) {
    }
}
