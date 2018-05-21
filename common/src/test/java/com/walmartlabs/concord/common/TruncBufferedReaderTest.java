package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TruncBufferedReaderTest {

    @Test
    public void testEmpty() throws Exception {
        String str = "";

        List<String> r = readLines(str);
        assertEquals(0, r.size());
    }

    @Test
    public void test1() throws Exception {
        String str = "1";

        List<String> r = readLines(str);
        assertEquals(1, r.size());
        assertEquals("1", r.get(0));
    }

    @Test
    public void test2() throws Exception {
        String str = "12";

        List<String> r = readLines(str, 2);
        assertEquals(1, r.size());
        assertEquals("12", r.get(0));
    }

    @Test
    public void test3() throws Exception {
        String str = "123456789";

        List<String> r = readLines(str, 2);
        assertEquals(1, r.size());
        assertEquals("12...[skipped 7 bytes]", r.get(0));
    }

    @Test
    public void test4() throws Exception {
        String str = "12\n3";

        List<String> r = readLines(str, 2);
        assertEquals(2, r.size());
        assertEquals("12", r.get(0));
        assertEquals("3", r.get(1));
    }

    @Test
    public void test5() throws Exception {
        String str = "123\n456";

        List<String> r = readLines(str, 2);
        assertEquals(2, r.size());
        assertEquals("12...[skipped 1 bytes]", r.get(0));
        assertEquals("45...[skipped 1 bytes]", r.get(1));
    }

    @Test
    public void test6() throws Exception {
        String str = "1\r\n23\n45\r6";

        List<String> r = readLines(str, 2);
        assertEquals(4, r.size());
        assertEquals("1", r.get(0));
        assertEquals("23", r.get(1));
        assertEquals("45", r.get(2));
        assertEquals("6", r.get(3));
    }

    @Test
    public void test7() throws Exception {
        String str = "1\r\n23\n45\r6\n";

        List<String> r = readLines(str, 2);
        assertEquals(4, r.size());
        assertEquals("1", r.get(0));
        assertEquals("23", r.get(1));
        assertEquals("45", r.get(2));
        assertEquals("6", r.get(3));
    }

    private List<String> readLines(String str) throws IOException {
        return readLines(str, TruncBufferedReader.DEFAULT_MAX_LINE_LENGTH);
    }

    private List<String> readLines(String str, int maxLineLength) throws IOException {
        List<String> result = new ArrayList<>();
        BufferedReader reader = new TruncBufferedReader(new InputStreamReader(new ByteArrayInputStream(str.getBytes())), maxLineLength);
        String line;
        while ((line = reader. readLine()) != null) {
            result.add(line);
        }
        return result;
    }
}
