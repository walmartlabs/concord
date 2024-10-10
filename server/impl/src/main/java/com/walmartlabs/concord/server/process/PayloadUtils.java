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

import com.walmartlabs.concord.common.DateTimeUtils;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.runtime.v2.model.ExclusiveMode;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.xml.bind.DatatypeConverter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class PayloadUtils {

    public static ExclusiveMode getExclusive(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.CONFIGURATION);
        Map<String, Object> exclusive = MapUtils.getMap(cfg, Constants.Request.EXCLUSIVE, Collections.emptyMap());
        if (exclusive.isEmpty()) {
            return null;
        }
        String group = MapUtils.getString(exclusive, "group");
        if (group == null || group.trim().isEmpty()) {
            throw new ProcessException(p.getProcessKey(), "Invalid exclusive mode: exclusive group not specified or empty");
        }
        ExclusiveMode.Mode mode = MapUtils.getEnum(exclusive, "mode", ExclusiveMode.Mode.class, ExclusiveMode.Mode.cancel);
        return ExclusiveMode.of(group, mode);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getRequirements(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.CONFIGURATION);
        return (Map<String, Object>) cfg.get(Constants.Request.REQUIREMENTS);
    }

    public static OffsetDateTime getStartAt(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return null;
        }

        String k = Constants.Request.START_AT_KEY;
        Object v = cfg.get(k);
        if (v == null) {
            return null;
        }

        if (v instanceof String iso) {
            OffsetDateTime t;
            try {
                t = DateTimeUtils.fromIsoString(iso);
            } catch (DateTimeParseException e) {
                throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' format, expected an ISO-8601 value, got: " + v);
            }

            if (t.isBefore(OffsetDateTime.now())) {
                throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' value, can't be in the past: " + v +
                        " Current server time: " + DatatypeConverter.printDateTime(Calendar.getInstance()));
            }

            return t;
        }

        throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' value, expected an ISO-8601 value, got: " + v);
    }

    public static Payload addSnapshots(Payload payload, List<Snapshot> l) {
        if (l == null || l.isEmpty()) {
            return payload;
        }

        List<Snapshot> result = new ArrayList<>();

        List<Snapshot> snapshots = payload.getHeader(Payload.REPOSITORY_SNAPSHOT);
        if (snapshots != null) {
            result.addAll(snapshots);
        }
        result.addAll(l);

        return payload.putHeader(Payload.REPOSITORY_SNAPSHOT, result);
    }

    private PayloadUtils() {
    }
}
