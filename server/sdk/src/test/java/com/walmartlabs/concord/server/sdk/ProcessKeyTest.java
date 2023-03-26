package com.walmartlabs.concord.server.sdk;

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

import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

public class ProcessKeyTest {

    @Test
    public void testNanos() {
        try {
            new ProcessKey(ProcessKey.from(UUID.randomUUID()), OffsetDateTime.now()
                    .with(ChronoField.NANO_OF_SECOND, 1));
            fail("must fail");
        } catch (IllegalArgumentException e) {
        }

        try {
            new ProcessKey(ProcessKey.from(UUID.randomUUID()), OffsetDateTime.now()
                    .with(ChronoField.NANO_OF_SECOND, 499));
            fail("must fail");
        } catch (IllegalArgumentException e) {
        }

        try {
            new ProcessKey(ProcessKey.from(UUID.randomUUID()), OffsetDateTime.now()
                    .with(ChronoField.NANO_OF_SECOND, 999));
            fail("must fail");
        } catch (IllegalArgumentException e) {
        }

        new ProcessKey(ProcessKey.from(UUID.randomUUID()), OffsetDateTime.now()
                .with(ChronoField.NANO_OF_SECOND, 0));
    }
}
