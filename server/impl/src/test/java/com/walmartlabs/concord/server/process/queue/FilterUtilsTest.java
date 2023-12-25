package com.walmartlabs.concord.server.process.queue;

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

import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import java.util.Collections;
import java.util.List;

import static com.walmartlabs.concord.server.process.queue.ProcessFilter.FilterType.REGEXP_MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FilterUtilsTest {

    @Test
    public void testRegularExpressionMatch() {
        MultivaluedHashMap<String, String> m = new MultivaluedHashMap<>();
        m.put("test.regexp", Collections.singletonList("myValue"));

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getQueryParameters()).thenReturn(m);

        List<ProcessFilter.JsonFilter> filter =  FilterUtils.parseJson("test", uriInfo);
        assertEquals(1, filter.size());
        assertEquals(REGEXP_MATCH, filter.get(0).type());
        assertEquals("myValue", filter.get(0).value());
    }
}
