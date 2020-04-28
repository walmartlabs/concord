package com.walmartlabs.concord.agent.logging;

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
import com.walmartlabs.concord.client.LogSegmentUpdateRequest;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.walmartlabs.concord.agent.logging.LogStatsParser.END_STATS;
import static com.walmartlabs.concord.agent.logging.LogStatsParser.START_STATS;
import static org.junit.Assert.*;

public class LogStatsParserTest {

    @Test
    public void test() throws Exception {
        LogSegmentStats stats = ImmutableLogSegmentStats.builder()
                .status(LogSegmentUpdateRequest.StatusEnum.OK)
                .errors(2)
                .warnings(10)
                .build();

        LogSegmentStats stats2 = ImmutableLogSegmentStats.builder()
                .status(LogSegmentUpdateRequest.StatusEnum.FAILED)
                .errors(21)
                .warnings(101)
                .build();

        byte[] statsAb = new ObjectMapper().writeValueAsBytes(stats);
        byte[] statsAb2 = new ObjectMapper().writeValueAsBytes(stats2);

        {
            byte[] ab = concat(START_STATS, statsAb, END_STATS);

            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertEquals(stats, result.stats());
            assertNull(result.chunk());
            assertEquals(ab.length, result.readPos());
        }

        {
            byte[] ab = concat(new byte[]{0}, START_STATS, statsAb, END_STATS);

            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertEquals(stats, result.stats());
            assertArrayEquals(new byte[]{0}, result.chunk());
            assertEquals(ab.length, result.readPos());
        }

        {
            byte[] ab = concat(new byte[]{0}, START_STATS, statsAb, END_STATS, new byte[]{4});

            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertEquals(stats, result.stats());
            assertArrayEquals(new byte[]{0, 4}, result.chunk());
            assertEquals(ab.length, result.readPos());
        }

        {
            byte[] ab = {0, 1, 2, 3, '>', '>', 4};

            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertNull(result.stats());
            assertArrayEquals(ab, result.chunk());
            assertEquals(ab.length, result.readPos());
        }

        {
            byte[] ab = concat(new byte[]{0}, START_STATS, new byte[]{1, 2, 3, 4});

            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertNull(result.stats());
            assertArrayEquals(new byte[]{0}, result.chunk());
            assertEquals(1, result.readPos());
        }

        {
            byte[] ab = concat(new byte[]{0}, START_STATS, new byte[]{1, 2, 3, 4, '>'});

            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertNull(result.stats());
            assertArrayEquals(new byte[]{0}, result.chunk());
            assertEquals(1, result.readPos());
        }

        {
            byte[] ab = concat(new byte[]{0}, START_STATS, statsAb, END_STATS, new byte[]{6}, START_STATS, new byte[]{9});
            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertNull(result.stats());
            assertArrayEquals(new byte[]{0, 6}, result.chunk());
            assertEquals(ab[result.readPos()], START_STATS[0]);

        }

        {
            byte[] ab = concat(new byte[]{0},
                    START_STATS, statsAb, END_STATS,
                    new byte[]{6},
                    START_STATS, statsAb2, END_STATS,
                    new byte[]{9});
            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertEquals(stats2, result.stats());
            assertArrayEquals(new byte[]{0, 6, 9}, result.chunk());
            assertEquals(ab.length, result.readPos());
        }

        {
            byte[] ab = concat(new byte[]{0, 1},
                    START_STATS, statsAb, END_STATS,
                    START_STATS, statsAb2, END_STATS);
            LogStatsParser.Result result = LogStatsParser.parse(ab, ab.length);

            assertEquals(stats2, result.stats());
            assertArrayEquals(new byte[]{0, 1}, result.chunk());
            assertEquals(ab.length, result.readPos());
        }
    }

    private byte[] concat(byte[]... items) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] item : items) {
            outputStream.write(item);
        }
        return outputStream.toByteArray();
    }
}
