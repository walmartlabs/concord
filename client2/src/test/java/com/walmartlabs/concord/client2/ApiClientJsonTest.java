package com.walmartlabs.concord.client2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApiClientJsonTest {

    @Test
    public void testParseDate() throws Exception {
        ObjectMapper om = ApiClient.createDefaultObjectMapper();

        Date date = new Date(1587500112000L);
        OffsetDateTime offsetDateTime = date.toInstant().atOffset(ZoneOffset.UTC);


        String toParse = om.writeValueAsString(offsetDateTime);
        toParse = toParse.substring(1, toParse.length() - 1);

        // format like a sever entries
        Date parsed = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX").
                parse(toParse);

        assertEquals(date, parsed);
    }

    @Test
    public void testObjectSerialize() throws Exception {
        ProjectEntry project = new ProjectEntry()
                .name("");

        String str = ApiClient.createDefaultObjectMapper().writeValueAsString(project);
        assertEquals("{\"name\":\"\"}", str);
    }

    @Test
    public void testEmptyCollectionSerialize() throws Exception {
        CreateUserRequest user = new CreateUserRequest()
                .username("test")
                .roles(Collections.emptySet());

        String str = ApiClient.createDefaultObjectMapper().writeValueAsString(user);
        assertEquals("{\"username\":\"test\",\"roles\":[]}", str);
    }
}
