package com.walmartlabs.concord.agent.cfg;

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

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutConfigurationTest {

    @Test
    void requiresPositiveFiniteBudgets() {
        assertEquals(Duration.ofSeconds(3), Utils.getTimeout(ConfigFactory.parseString("budget = 3 seconds"), "budget", null));
        assertThrows(com.typesafe.config.ConfigException.class, () -> Utils.getTimeout(ConfigFactory.parseString("budget = unlimited"), "budget", Duration.ofMinutes(1)));
        assertEquals(Duration.ofMinutes(1), Utils.getTimeout(ConfigFactory.empty(), "budget", Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> Utils.getTimeout(ConfigFactory.parseString("budget = 0 seconds"), "budget", null));
        assertThrows(IllegalArgumentException.class, () -> Utils.getTimeout(ConfigFactory.parseString("budget = -1 seconds"), "budget", null));
    }
}
