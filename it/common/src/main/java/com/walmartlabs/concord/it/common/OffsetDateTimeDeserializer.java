package com.walmartlabs.concord.it.common;

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

import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Supports the Server's timestamp format.
 */
public class OffsetDateTimeDeserializer extends InstantDeserializer<OffsetDateTime> {

    private static final long serialVersionUID = 1L;

    public OffsetDateTimeDeserializer() {
        super(OffsetDateTime.class, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
                OffsetDateTime::from,
                a -> OffsetDateTime.ofInstant(Instant.ofEpochMilli(a.value), a.zoneId),
                a -> OffsetDateTime.ofInstant(Instant.ofEpochSecond(a.integer, a.fraction), a.zoneId),
                (d, z) -> d.withOffsetSameInstant(z.getRules().getOffset(d.toLocalDateTime())),
                true);
    }
}
