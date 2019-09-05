package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.xml.bind.DatatypeConverter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;

public final class PayloadUtils {

    public static Map<String, Object> getExclusive(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.CONFIGURATION);
        return MapUtils.getMap(cfg, Constants.Request.EXCLUSIVE, Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getRequirements(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.CONFIGURATION);
        return (Map<String, Object>) cfg.get(InternalConstants.Request.REQUIREMENTS);
    }

    public static Instant getStartAt(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return null;
        }

        String k = Constants.Request.START_AT_KEY;
        Object v = cfg.get(k);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            Calendar c;
            try {
                c = DatatypeConverter.parseDateTime((String) v);
            } catch (DateTimeParseException e) {
                throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' format, expected an ISO-8601 value, got: " + v);
            }

            if (c.before(Calendar.getInstance())) {
                throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' value, can't be in the past: " + v);
            }

            return c.toInstant();
        }

        throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' value, expected an ISO-8601 value, got: " + v);
    }

    private PayloadUtils() {
    }
}
