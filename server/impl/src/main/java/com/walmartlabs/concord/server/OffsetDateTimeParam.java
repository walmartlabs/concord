package com.walmartlabs.concord.server;

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

import com.walmartlabs.concord.common.DateTimeUtils;

import java.io.Serializable;
import java.time.OffsetDateTime;

public class OffsetDateTimeParam implements Serializable, WrappedValue<OffsetDateTime> {

    private static final long serialVersionUID = 1L;

    public static OffsetDateTimeParam valueOf(String s) {
        OffsetDateTime value = DateTimeUtils.fromIsoString(s);
        return new OffsetDateTimeParam(value);
    }

    public OffsetDateTimeParam(OffsetDateTime value) {
        this.value = value;
    }

    private final OffsetDateTime value;

    @Override
    public OffsetDateTime getValue() {
        return value;
    }
}
