package com.walmartlabs.concord.it.console;

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

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class LoginIT extends AbstractConsoleIT {

    @Test
    public void test() throws Exception {
        login(defaultApiKey());
        waitFor(By.id("concordLogo"));
    }

    @Test
    public void refresh() throws Exception {
        login(defaultApiKey());
        waitFor(By.id("concordLogo"));

        WebDriver driver = getDriver();
        driver.navigate().refresh();

        waitFor(By.id("concordLogo"));
    }
}
