package com.walmartlabs.concord.policyengine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ContainerPolicy {

    private final ContainerRule rule;

    public ContainerPolicy(ContainerRule rule) {
        this.rule = rule;
    }

    public CheckResult<ContainerRule, Object> check(Map<String, Object> containerOptions) {
        if (rule == null) {
            return CheckResult.success();
        }

        List<CheckResult.Item<ContainerRule, Object>> deny = new ArrayList<>();

        if (rule.maxCpu() != null) {
            Integer actualCpu = getInt("cpu", containerOptions);
            if (actualCpu != null && actualCpu > rule.maxCpu()) {
                deny.add(new CheckResult.Item<>(rule, actualCpu, "Max CPU exceeded: " + actualCpu));
            }
        }

        if (rule.maxRam() != null) {
            String actualRamStr = getString("ram", containerOptions);
            Long maxRam = parseRam(rule.maxRam());
            Long actualRam = parseRam(actualRamStr);
            if (actualRam != null && actualRam > maxRam) {
                deny.add(new CheckResult.Item<>(rule, actualRamStr, "Max RAM exceeded: " + actualRamStr));
            }
        }

        return new CheckResult<>(Collections.emptyList(), deny);
    }

    private static Integer getInt(String name, Map<String, Object> params) {
        Object o = params.get(name);
        if (o == null) {
            return null;
        }

        if (o instanceof Number) {
            return ((Number)o).intValue();
        }
        throw new IllegalArgumentException("Expected an integer value '" + name + "', got: " + o);
    }

    private static String getString(String name, Map<String, Object> params) {
        Object o = params.get(name);
        if (o == null) {
            return null;
        }

        if (o instanceof String) {
            return ((String)o).trim();
        }
        throw new IllegalArgumentException("Expected a string value '" + name + "', got: " + o);
    }

    private static Long parseRam(String str) {
        if (str == null) {
            return null;
        }

        long c = 1L;
        if (str.endsWith("k")) {
            c = 1024L;
        } else if (str.endsWith("m")) {
            c = 1024 * 1024L;
        } else if (str.endsWith("g")) {
            c = 1024 * 1024 * 1024L;
        }

        return Long.valueOf(str.substring(0, str.length() - 1).trim()) * c;
    }
}
