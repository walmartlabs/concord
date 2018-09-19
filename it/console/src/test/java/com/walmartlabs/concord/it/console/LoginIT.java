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

import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class LoginIT {

    private static final String ADMIN_API_KEY = "auBy4eDWrKWsyhiDp3AQiw";

    @Rule
    public WebDriverRule rule = new WebDriverRule();

    private static void waitFor(WebDriver driver, By by) {
        ExpectedCondition<Boolean> condition = d -> {
            try {
                d.findElement(by);
                return true;
            } catch (NoSuchElementException e) {
                return false;
            }
        };

        WebDriverWait wait = new WebDriverWait(driver, 10);
        wait.until(condition);
    }

    @Test
    public void test() {
        WebDriver driver = rule.getDriver();

        driver.get(ITConstants.BASE_URL + "/#/login?useApiKey=true");

        WebElement apiKey = driver.findElement(By.name("apiKey"));
        apiKey.sendKeys(ADMIN_API_KEY);

        WebElement loginButton = driver.findElement(By.id("loginButton"));
        loginButton.click();

        waitFor(driver, By.id("concordLogo"));
    }
}
