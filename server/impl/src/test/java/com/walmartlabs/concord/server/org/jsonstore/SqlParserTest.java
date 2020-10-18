package com.walmartlabs.concord.server.org.jsonstore;

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

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SqlParserTest {

    @Test
    public void test() throws Exception {
        List<String> queries = parseQueries("queries.txt");

        for (int i = 0; i < queries.size(); i++) {
            String q = queries.get(i);

            try {
                CCJSqlParserUtil.parse(q);
            } catch (JSQLParserException e) {
                System.out.println("#" + i + ": " + q);
                throw e;
            }
        }
    }

    public static List<String> parseQueries(String resourceName) throws IOException {
        List<String> queries = new ArrayList<>();

        try (InputStream in = SqlParserTest.class.getResourceAsStream(resourceName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            int brk = 0;

            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    brk++;
                    continue;
                }

                if (brk >= 2) {
                    queries.add(sb.toString());
                    sb = new StringBuilder();
                }

                sb.append(line).append(" ");
                brk = 0;
            }
        }
        return queries;
    }
}
