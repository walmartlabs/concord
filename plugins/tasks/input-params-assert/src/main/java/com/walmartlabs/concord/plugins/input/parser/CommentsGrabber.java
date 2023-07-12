package com.walmartlabs.concord.plugins.input.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommentsGrabber {

    public Map<String, List<String>> grab(Path file) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            int lineNum = findKey(reader, "flows:");
            if (lineNum == -1) {
                return result;
            }

            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (isNewYamlBlock(line)) {
                    break;
                } else if (line.trim().startsWith("#")) {
                    lines.add(line);
                } else if (isFlowName(line)) {
                    if (!lines.isEmpty()) {
                        line = line.trim();
                        String flowName = line.substring(0, line.length() - 1);
                        result.put(flowName, new ArrayList<>(lines));
                        lines.clear();
                    }
                }
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException("Error while reading " + file + ": " + e.getMessage());
        }
    }

    private static int findKey(BufferedReader reader, String key) throws IOException {
        String line;
        int lineNum = 0;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            if (key.equals(line.trim())) {
                return lineNum;
            }
        }
        return -1;
    }

    private static boolean isNewYamlBlock(String line) {
        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
            return false;
        }

        return !Character.isWhitespace(line.charAt(0));
    }

    private static boolean isFlowName(String line) {
        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
            return false;
        }

        return Character.isWhitespace(line.charAt(0));
    }
}
