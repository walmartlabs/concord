package com.walmartlabs.concord.it.console;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;

@Timeout(value = 60000, unit = TimeUnit.MILLISECONDS)
public class LoginIT {

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void test() throws Exception {
        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.waitFor(By.id("concordLogo"));
    }

    @Test
    public void refresh() throws Exception {
        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.waitFor(By.id("concordLogo"));

        WebDriver driver = consoleRule.getDriver();
        driver.navigate().refresh();

        consoleRule.waitFor(By.id("concordLogo"));
    }
}
