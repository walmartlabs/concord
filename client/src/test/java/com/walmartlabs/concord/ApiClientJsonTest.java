package com.walmartlabs.concord;

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

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class ApiClientJsonTest {

    @Test
    public void testParseDate() throws ParseException {
        ApiClient apiClient = new ApiClient();
        JSON json = apiClient.getJSON();

        Date date = new Date(1587500112000L);
        OffsetDateTime offsetDateTime = date.toInstant().atOffset(ZoneOffset.UTC);

        String toParse = json.serialize(offsetDateTime);
        toParse = toParse.substring(1, toParse.length() - 1);

        // format like a sever entries
        Date parsed = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX").
                parse(toParse);

        assertEquals(date, parsed);
    }
}
