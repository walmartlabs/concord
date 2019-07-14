package com.walmartlabs.concord.project.yaml;

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

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.project.yaml.converter.StepConverter;
import com.walmartlabs.concord.project.yaml.model.YamlTrigger;
import com.walmartlabs.concord.sdk.Constants;
import io.takari.bpm.model.SourceMap;

import java.util.*;

public final class YamlTriggersConverter {

    private static final Map<String, TriggerConverter> converters = createConverters();
    private static final TriggerConverter defaultConverter = new DefaultTriggerConverter();

    private static Map<String, TriggerConverter> createConverters() {
        Map<String, TriggerConverter> converters = new HashMap<>();
        converters.put("manual", new ManualTriggerConverter());
        return converters;
    }

    public static List<Trigger> convert(List<YamlTrigger> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return null;
        }

        List<Trigger> result = new ArrayList<>();
        for (YamlTrigger t : triggers) {
            TriggerConverter converter = converters.getOrDefault(t.getName(), defaultConverter);
            result.add(converter.convert(t));
        }

        return result;
    }

    private static class ManualTriggerConverter extends AbstractTriggerConverter {

        private static final String[] TRIGGER_CONFIG_KEYS = {
                "name",
                Constants.Request.ENTRY_POINT_KEY,
                Constants.Trigger.EXCLUSIVE_GROUP
        };

        protected ManualTriggerConverter() {
            super(TRIGGER_CONFIG_KEYS);
        }
    }

    private static class DefaultTriggerConverter extends AbstractTriggerConverter {

        private static final String[] TRIGGER_CONFIG_KEYS = {
                Constants.Trigger.USE_INITIATOR,
                Constants.Trigger.USE_EVENT_COMMIT_ID,
                Constants.Request.ENTRY_POINT_KEY,
                Constants.Trigger.EXCLUSIVE_GROUP
        };

        public DefaultTriggerConverter() {
            super(TRIGGER_CONFIG_KEYS);
        }
    }

    private static abstract class AbstractTriggerConverter implements TriggerConverter {

        private final String[] cfgKeys;

        protected AbstractTriggerConverter(String[] cfgKeys) {
            this.cfgKeys = cfgKeys;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Trigger convert(YamlTrigger trigger) {
            String name = trigger.getName();

            Map<String, Object> opts = (Map<String, Object>) StepConverter.deepConvert(trigger.getOptions());

            List<String> activeProfiles = (List<String>) opts.remove(Constants.Request.ACTIVE_PROFILES_KEY);
            Map<String, Object> arguments = (Map<String, Object>) opts.remove(Constants.Request.ARGUMENTS_KEY);

            Map<String, Object> cfg = new HashMap<>();
            for (String key : cfgKeys) {
                Object v = opts.remove(key);
                if (v != null) {
                    cfg.put(key, v);
                }
            }

            return new Trigger(name, activeProfiles, arguments, opts, cfg, convertSourceMap(trigger));
        }

        private static SourceMap convertSourceMap(YamlTrigger t) {
            String name = t.getName();
            JsonLocation l = t.getLocation();
            return new SourceMap(SourceMap.Significance.HIGH,
                    String.valueOf(l.getSourceRef()),
                    l.getLineNr(),
                    l.getColumnNr(),
                    "Trigger: " + name);
        }
    }

    private interface TriggerConverter {

        Trigger convert(YamlTrigger trigger);
    }

    private YamlTriggersConverter() {
    }
}
