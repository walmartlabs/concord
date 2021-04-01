package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

class AnsibleInventoryParser {
    private final Set<String> hosts;

    AnsibleInventoryParser() {
        this.hosts = new LinkedHashSet<>();
    }

    /**
     * Counts unique hosts within a JSON Ansible inventory
     * @param is InputStream providing JSON data
     * @return number of unique hosts in the inventory
     */
    public int countInventoryHosts(InputStream is) throws AnsibleInventoryException {
        JsonFactory jsonFactory = new JsonFactory();

        try (JsonParser jsonParser = jsonFactory.createParser(is)) {
            if (jsonParser.nextToken() != JsonToken.START_OBJECT) {
                throw new AnsibleInventoryException("Inventory content is not an object.");
            }

            // Iterate over the tokens until the end of the object
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                readObject(jsonParser);
            }

            return hosts.size();

        } catch (Exception e) {
            throw new AnsibleInventoryException("Error detecting inventory size: " + e.getMessage());
        }
    }

    private void readArray(JsonParser jsonParser) throws IOException {
        String property = jsonParser.getCurrentName();

        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
            if (property.equals("hosts")) {
                hosts.add(jsonParser.getValueAsString());
            }
        }
    }

    private void readObject(JsonParser jsonParser) throws IOException {
        JsonToken jsonToken = jsonParser.nextToken();

        while (jsonToken != JsonToken.END_OBJECT) {
            jsonToken = jsonParser.nextToken();

            if (jsonParser.currentToken() == JsonToken.START_ARRAY) {
                readArray(jsonParser);
            } else if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
                readObject(jsonParser);
            }
        }
    }
}
