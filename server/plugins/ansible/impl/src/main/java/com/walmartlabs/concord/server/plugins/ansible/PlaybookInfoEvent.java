package com.walmartlabs.concord.server.plugins.ansible;

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

import com.walmartlabs.concord.sdk.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlaybookInfoEvent extends AbstractAnsibleEvent {

    public static PlaybookInfoEvent from(EventProcessor.Event e) {
        if (!e.eventType().equals(Constants.ANSIBLE_PLAYBOOK_INFO)) {
            return null;
        }

        return new PlaybookInfoEvent(e);
    }

    private PlaybookInfoEvent(EventProcessor.Event e) {
        super(e);
    }

    public String getPlaybookName() {
        return MapUtils.assertString(e.payload(), "playbook");
    }

    public int getHostsCount() {
        return MapUtils.assertInt(e.payload(), "uniqueHosts");
    }

    public List<Play> getPlays() {
        List<Map<String, Object>> plays = MapUtils.assertList(e.payload(), "plays");
        return plays.stream()
                .map(Play::from)
                .collect(Collectors.toList());
    }

    public long getTotalWork() {
        return MapUtils.assertNumber(e.payload(), "totalWork").longValue();
    }

    public Integer retryCount() {
        Object v = e.payload().get("currentRetryCount");

        if (v instanceof Number) {
            return ((Number) v).intValue();
        }

        if (v instanceof String) {
            return Integer.parseInt((String)v);
        }

        return null;
    }

    public String getStatus() {
        return MapUtils.assertString(e.payload(), "status");
    }

    public static class Play {

        private final Map<String, Object> params;

        private Play(Map<String, Object> params) {
            this.params = params;
        }

        public static Play from(Map<String, Object> p) {
            return new Play(p);
        }

        public UUID getId() {
            return MapUtils.assertUUID(params, "id");
        }

        public String getName() {
            return MapUtils.getString(params, "play");
        }

        public long getHostCount() {
            return MapUtils.assertNumber(params, "hosts").longValue();
        }

        public int getTaskCount() {
            return getTasks().size();
        }

        public List<Map<String, Object>> getTasks() {
            return MapUtils.assertList(params, "tasks");
        }
    }
}
