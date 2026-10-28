package com.walmartlabs.concord.client2;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiContractTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "server, ../server/impl/target/classes/com/walmartlabs/concord/server/swagger/swagger.yaml",
            "ansible, ../server/plugins/ansible/impl/target/classes/com/walmartlabs/concord/server/plugins/ansible/swagger/swagger.yaml",
            "noderoster, ../server/plugins/noderoster/impl/target/classes/com/walmartlabs/concord/server/plugins/noderoster/swagger/swagger.yaml"
    })
    void preservesPublishedContract(String api, String inputSpec) throws Exception {
        // Read the same files as the client generators, never fall back to an installed server/plugin JAR.
        var generated = Path.of(inputSpec);
        assertTrue(Files.isRegularFile(generated),
                () -> "Missing " + inputSpec + "; run the corresponding server/plugin compile phase first");
        var baselineResource = "/openapi/" + api + "-api.yaml";
        try (var baseline = getClass().getResourceAsStream(baselineResource)) {
            assertNotNull(baseline, "Missing OpenAPI baseline: " + baselineResource);

            var yaml = new ObjectMapper(new YAMLFactory());
            var expected = yaml.readTree(baseline);
            var actualBytes = Files.readAllBytes(generated);
            var actual = yaml.readTree(actualBytes);
            var differences = new ArrayList<String>();
            compare("", expected, actual, differences);

            var output = Path.of("target", "openapi-contract", api);
            Files.createDirectories(output);
            Files.write(output.resolve("actual.yaml"), actualBytes);
            Files.write(output.resolve("diff.txt"), differences);

            assertTrue(differences.isEmpty(), () -> api + " OpenAPI contract changed:\n"
                    + String.join("\n", differences)
                    + "\nSee " + output + "/{actual.yaml,diff.txt}. "
                    + "Review changes before updating src/test/resources" + baselineResource + ".");
        }
    }

    private static void compare(String pointer, JsonNode expected, JsonNode actual, List<String> differences) {
        // JsonNode equality ignores object field order, while preserving array order and all values.
        if (expected.equals(actual)) {
            return;
        }
        if (expected.isObject() && actual.isObject()) {
            var names = new TreeSet<String>();
            expected.fieldNames().forEachRemaining(names::add);
            actual.fieldNames().forEachRemaining(names::add);
            for (var name : names) {
                var escaped = name.replace("~", "~0").replace("/", "~1");
                compare(pointer + "/" + escaped, expected.path(name), actual.path(name), differences);
            }
        } else if (expected.isArray() && actual.isArray()) {
            for (var i = 0; i < Math.max(expected.size(), actual.size()); i++) {
                compare(pointer + "/" + i, expected.path(i), actual.path(i), differences);
            }
        } else {
            differences.add(pointer + ": expected " + describe(expected) + ", actual " + describe(actual));
        }
    }

    private static String describe(JsonNode node) {
        if (node.isMissingNode()) {
            return "<missing>";
        }
        if (node.isObject()) {
            return "object with " + node.size() + " fields";
        }
        if (node.isArray()) {
            return "array with " + node.size() + " elements";
        }
        return node.toString();
    }
}
