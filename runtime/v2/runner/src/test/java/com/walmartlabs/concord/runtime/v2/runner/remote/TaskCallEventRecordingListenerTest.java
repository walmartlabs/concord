package com.walmartlabs.concord.runtime.v2.runner.remote;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TaskCallEventRecordingListenerTest {

    private static final ObjectMapper om = new ObjectMapper();

    @Test
    public void testMaskVars() throws Exception {
        String in = "{" +
                    "   \"a\":1," +
                    "   \"b\":2," +
                    "   \"c\":{" +
                    "      \"c1\":3," +
                    "      \"c2\":4," +
                    "      \"c3\":{" +
                    "         \"c31\":5," +
                    "         \"c32\":6" +
                    "      }" +
                    "   }" +
                    "}";

        List<String> blackList = Arrays.asList("b", "c.c1", "c.c3.c31");
        Map<String, Object> result = TaskCallEventRecordingListener.maskVars(vars(in), blackList);

        String expected = "{" +
                          "   \"a\":1," +
                          "   \"b\":\"***\"," +
                          "   \"c\":{" +
                          "      \"c1\":\"***\"," +
                          "      \"c2\":4," +
                          "      \"c3\":{" +
                          "         \"c31\":\"***\"," +
                          "         \"c32\":6" +
                          "      }" +
                          "   }" +
                          "}";
        assertEquals(vars(expected), result);
    }

    @Test
    public void testMaskVarsUnmodifiable() {
        Map<String, Object> vars =
                Collections.singletonMap("x",
                        Collections.singletonMap("y",
                                Collections.singletonMap("z", 123)));

        List<String> blackList = Collections.singletonList("x.y.z");
        Map<String, Object> result = TaskCallEventRecordingListener.maskVars(vars, blackList);
        assertEquals("{x={y={z=***}}}", result.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> vars(String in) throws JsonProcessingException {
        return om.readValue(in, Map.class);
    }
}
