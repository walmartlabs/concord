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
            String flowsLine = findKey(reader, "flows:");
            if (flowsLine == null) {
                return result;
            }

            int flowsIndent = getIndentLevel(flowsLine);

            int flowNameIndent = -1;
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                if (line.trim().startsWith("#")) {
                    lines.add(line);
                    continue;
                }

                int lineIndentLevel = getIndentLevel(line);
                if (lineIndentLevel <= flowsIndent) {
                    // another tag (e.g. profiles ...)
                    break;
                }

                if (flowNameIndent < 0) {
                    flowNameIndent = lineIndentLevel;
                }

                if (flowNameIndent == lineIndentLevel) {
                    if (!lines.isEmpty()) {
                        line = line.trim();
                        String flowName = line.substring(0, line.length() - 1);
                        result.put(flowName, new ArrayList<>(lines));
                        lines.clear();
                    }
                } else {
                    lines.clear();
                }
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException("Error while reading " + file + ": " + e.getMessage());
        }
    }

    private static String findKey(BufferedReader reader, String key) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (key.equals(line.trim())) {
                return line;
            }
        }
        return null;
    }

    private static int getIndentLevel(String line) {
        int count = 0;

        while (count < line.length() && Character.isWhitespace(line.charAt(count))) {
            count++;
        }

        return count;
    }
}
