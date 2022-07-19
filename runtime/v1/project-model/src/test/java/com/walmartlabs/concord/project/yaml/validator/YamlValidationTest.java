package com.walmartlabs.concord.project.yaml.validator;

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

import com.walmartlabs.concord.project.yaml.AbstractYamlParserTest;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class YamlValidationTest extends AbstractYamlParserTest {

    @Test
    public void test002() {
        try {
            deploy("validator/002.yml");
            fail("exception expected");
        } catch (Exception e) {
            Throwable t = e.getCause();
            assertTrue(t.getMessage().contains("checkpoint 'one'"));
            assertTrue(t.getMessage().contains("already defined at"));
        }
    }
}
