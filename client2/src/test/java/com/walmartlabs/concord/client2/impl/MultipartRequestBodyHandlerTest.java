package com.walmartlabs.concord.client2.impl;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultipartRequestBodyHandlerTest {

    @Test
    public void test2() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("string-field", "this stuff");
        data.put("byte[]-field", "byte array".getBytes());
        data.put("inputstream-field", new ByteArrayInputStream("my input stream".getBytes()));
        data.put("boolean-field", true);
        data.put("json-field", Collections.singletonMap("k", "v"));
        data.put("string[]-field", new String[] {"one", "two"});
        data.put("uuid-field", UUID.fromString("f8d30c37-4c84-4817-9cb8-23b27a54c459"));

        MultipartBuilder mpb = new MultipartBuilder("e572b648-941a-4648-97ed-0e3c5350f0ad");
        HttpEntity entity = MultipartRequestBodyHandler.handle(mpb, new ObjectMapper(), data);

        try (InputStream is = entity.getContent()) {
            String str = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(body, str);
            assertEquals("multipart/form-data; boundary=e572b648-941a-4648-97ed-0e3c5350f0ad", entity.contentType().toString());
        }
    }

    private static final String body = "--e572b648-941a-4648-97ed-0e3c5350f0ad\r\n" +
            "Content-Disposition: form-data; name=\"string-field\"\r\n" +
            "Content-Length: 10\r\n" +
            "\r\n" +
            "this stuff\r\n" +
            "--e572b648-941a-4648-97ed-0e3c5350f0ad\r\n" +
            "Content-Disposition: form-data; name=\"byte[]-field\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Length: 10\r\n" +
            "\r\n" +
            "byte array\r\n" +
            "--e572b648-941a-4648-97ed-0e3c5350f0ad\r\n" +
            "Content-Disposition: form-data; name=\"inputstream-field\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "\r\n" +
            "my input stream\r\n" +
            "--e572b648-941a-4648-97ed-0e3c5350f0ad\r\n" +
            "Content-Disposition: form-data; name=\"boolean-field\"\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: 4\r\n" +
            "\r\n" +
            "true\r\n" +
            "--e572b648-941a-4648-97ed-0e3c5350f0ad\r\n" +
            "Content-Disposition: form-data; name=\"json-field\"\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: 9\r\n" +
            "\r\n" +
            "{\"k\":\"v\"}\r\n" +
            "--e572b648-941a-4648-97ed-0e3c5350f0ad\r\n" +
            "Content-Disposition: form-data; name=\"string[]-field\"\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: 7\r\n" +
            "\r\n" +
            "one,two\r\n" +
            "--e572b648-941a-4648-97ed-0e3c5350f0ad\r\n" +
            "Content-Disposition: form-data; name=\"uuid-field\"\r\n" +
            "Content-Length: 36\r\n" +
            "\r\n" +
            "f8d30c37-4c84-4817-9cb8-23b27a54c459\r\n" +
            "--e572b648-941a-4648-97ed-0e3c5350f0ad--" +
            "\r\n";
}
