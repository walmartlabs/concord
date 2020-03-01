package com.walmartlabs.concord.server.events.oneops;

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

import com.walmartlabs.concord.server.events.DefaultEventFilter;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;

import java.util.*;

public abstract class OneOpsTriggerProcessor {

    private static final String ORG_KEY = "org";
    private static final String ASM_KEY = "asm";
    private static final String ENV_KEY = "env";
    private static final String PLATFORM_KEY = "platform";
    private static final String STATE_KEY = "state";
    private static final String TYPE_KEY = "type";
    private static final String COMPONENT_KEY = "component";
    private static final String SOURCE_KEY = "source";
    private static final String SUBJECT_KEY = "subject";
    private static final String DEPLOYMENT_STATE_KEY = "deploymentState";
    private static final String IPS_KEY = "ips";
    private static final String AUTHOR_KEY = "author";
    private static final String EVENT_SOURCE = "oneops";

    private final TriggersDao triggersDao;
    private final int version;

    public OneOpsTriggerProcessor(TriggersDao triggersDao, int version) {
        this.triggersDao = triggersDao;
        this.version = version;
    }

    public void process(Map<String, Object> event, List<Result> result) {
        List<TriggerEntry> triggers = triggersDao.list(EVENT_SOURCE, version);

        Map<String, Object> triggerConditions = buildConditions(event);
        enrichTriggerConditions(triggerConditions, version);
        Map<String, Object> triggerEvent = buildTriggerEvent(event, triggerConditions);

        for (TriggerEntry triggerEntry : triggers) {
            if (DefaultEventFilter.filter(triggerConditions, triggerEntry)) {
                result.add(Result.from(triggerEvent, triggerEntry));
            }
        }
    }

    private static void enrichTriggerConditions(Map<String, Object> triggerConditions, Integer version) {
        triggerConditions.put("version", version);
    }

    private static Map<String, Object> buildTriggerEvent(Map<String, Object> event,
                                                         Map<String, Object> conditions) {

        Map<String, Object> result = new HashMap<>(conditions);
        result.put("payload", event);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildConditions(Map<String, Object> event) {
        Map<String, Object> cis = getCis(event);
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");

        Map<String, Object> result = new HashMap<>();
        result.put(STATE_KEY, get("ciState", cis));
        result.put(COMPONENT_KEY, get("ciClassName", cis));
        result.put(TYPE_KEY, get("type", event));
        result.put(SOURCE_KEY, get("source", event));
        result.put(SUBJECT_KEY, get("subject", event));
        result.put(DEPLOYMENT_STATE_KEY, get("deploymentState", payload));

        result.put(IPS_KEY, getIPs(event));
        result.put(AUTHOR_KEY, payload.get("createdBy"));

        // example: /testing/twst/localtest/bom/sts/1
        //          / org   /asm /env      /.../platform/...
        String[] nsPath = getNsPath(event);
        if (nsPath != null) {
            addKey(ORG_KEY, nsPath, 0, result);
            addKey(ASM_KEY, nsPath, 1, result);
            addKey(ENV_KEY, nsPath, 2, result);
            // ignore bom
            addKey(PLATFORM_KEY, nsPath, 4, result);
        }

        return result;
    }

    private static String get(String key, Map<String, Object> event) {
        return String.valueOf(event.get(key));
    }

    private static String[] getNsPath(Map<String, Object> event) {
        Map<String, Object> cis = getCis(event);
        if (cis == null) {
            return null;
        }

        String nsPath = (String) cis.get("nsPath");
        if (nsPath == null) {
            return new String[0];
        }

        if (nsPath.startsWith("/")) {
            nsPath = nsPath.substring(1);
        }

        return nsPath.split("/");
    }

    private static Map<String, Object> getCis(Map<String, Object> event) {
        List<Map<String, Object>> cisItems = getCisItems(event);
        if (cisItems.isEmpty()) {
            return Collections.emptyMap();
        }
        return cisItems.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getCisItems(Map<String, Object> event) {
        Object cisElement = event.get("cis");
        if (cisElement == null) {
            return Collections.emptyList();
        }

        if (cisElement instanceof List) {
            return (List<Map<String, Object>>) cisElement;
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getIPs(Map<String, Object> event) {
        List<Map<String, Object>> cisItems = getCisItems(event);
        if (cisItems.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        for (Map<String, Object> i : cisItems) {
            Object attrs = i.get("ciAttributes");
            if (attrs == null) {
                continue;
            }

            Map<String, Object> m = (Map<String, Object>) attrs;
            Object v = m.get("public_ip");
            if (v == null) {
                continue;
            }

            result.add(v.toString());
        }
        return result;
    }

    private static void addKey(String key, String[] values, int valueIndex, Map<String, Object> result) {
        if (values.length > valueIndex) {
            result.put(key, values[valueIndex]);
        }
    }

    public static class Result {

        private final Map<String, Object> event;

        private final List<TriggerEntry> triggers;

        public Result(Map<String, Object> event, List<TriggerEntry> triggers) {
            this.event = event;
            this.triggers = triggers;
        }

        public Map<String, Object> event() {
            return event;
        }

        public List<TriggerEntry> triggers() {
            return triggers;
        }

        static Result from(Map<String, Object> event, TriggerEntry trigger) {
            return new OneOpsTriggerProcessor.Result(event, Collections.singletonList(trigger));
        }
    }
}
