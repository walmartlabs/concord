package com.walmartlabs.concord.server.org.inventory;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.common.ConfigurationUtils;

import java.io.IOException;
import java.util.*;

public final class JsonBuilder {

    private static ObjectMapper mapper = new ObjectMapper();

    public static Object build(List<InventoryDataItem> dataItems) throws IOException {
        ObjectNode root = mapper.createObjectNode();

        List<InventoryDataItem> items = new ArrayList<>(dataItems);
        items.sort(Comparator.comparingInt(InventoryDataItem::getLevel).reversed());

        for(InventoryDataItem item : items) {
            String[] paths = normalizePath(item.getPath()).split("/");
            ObjectNode pathNode = root;
            for(int i = 0; i < paths.length - 1; i++) {
                String p = paths[i];
                pathNode = getOrCreateNode(pathNode, p);
            }
            String fieldName = paths[paths.length - 1];
            pathNode.set(fieldName, merge(pathNode.get(fieldName), item.getData()));
        }

        return mapper.treeToValue(root, Object.class);
    }

    @SuppressWarnings("unchecked")
    private static JsonNode merge(JsonNode node, Object data) throws IOException {
        if (node == null || data == null) {
            return mapper.valueToTree(data);
        }

        Object currentValue = mapper.treeToValue(node, Object.class);
        if (currentValue instanceof Map && data instanceof Map) {
            Map<String, Object> current = (Map<String, Object>) currentValue;
            Map<String, Object> newData = (Map<String, Object>) data;

            return mapper.valueToTree(ConfigurationUtils.deepMerge(current, newData));
        }

        return mapper.valueToTree(data);
    }

    private static ObjectNode getOrCreateNode(ObjectNode pathNode, String p) {
        JsonNode result = pathNode.get(p);
        if (result == null) {
            result = mapper.createObjectNode();
        }
        pathNode.set(p, result);
        if (!(result instanceof ObjectNode)) {
            throw new RuntimeException("Can't add attributes into " + result.getNodeType() + " node, with path: '" + p + "'");
        }
        return (ObjectNode)result;
    }

    private static String normalizePath(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    private JsonBuilder() {
    }
}
