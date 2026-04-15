package com.walmartlabs.concord.it.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.it.common.ITUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 60000, unit = TimeUnit.MILLISECONDS)
public class NavigationIT {

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testTopBarAndProfileTokenNavigation() throws Exception {
        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.waitFor(By.id("concordLogo"));

        openDropdownWithItem("About");
        clickVisibleDropdownItem("About");
        waitForUrlToEndWith("/#/about");

        openDropdownWithItem("Profile");
        clickVisibleDropdownItem("Profile");
        waitForUrlToEndWith("/#/profile/api-token");
        waitForDisplayed(By.xpath("//h4[normalize-space(.)='API Tokens']"));

        clickButton("New token");
        waitForUrlToEndWith("/#/profile/api-token/_new");
        waitForDisplayed(By.xpath("//h4[normalize-space(.)='New API Token']"));

        var tokenName = "token_" + ITUtils.randomString();
        var nameInput = waitForDisplayed(By.name("name"));
        nameInput.sendKeys(tokenName);

        clickButton("Generate");
        waitForDisplayed(By.xpath("//*[contains(normalize-space(.), 'API Token created')]"));

        clickButton("Done");
        waitForUrlToEndWith("/#/profile/api-token");
        waitForDisplayed(By.xpath("//h4[normalize-space(.)='API Tokens']"));
    }

    private static void openDropdownWithItem(String itemText) {
        waitForDisplayed(By.xpath(
                "//div[contains(concat(' ', normalize-space(@class), ' '), ' dropdown ')" +
                        " and .//div[contains(concat(' ', normalize-space(@class), ' '), ' menu ')" +
                        "]//div[contains(concat(' ', normalize-space(@class), ' '), ' item ')" +
                        " and contains(normalize-space(.), '" + itemText + "')]]"))
                .click();
    }

    private static void clickVisibleDropdownItem(String itemText) {
        waitForDisplayed(By.xpath(
                "//div[contains(concat(' ', normalize-space(@class), ' '), ' visible ')" +
                        " and contains(concat(' ', normalize-space(@class), ' '), ' menu ')" +
                        "]//div[contains(concat(' ', normalize-space(@class), ' '), ' item ')" +
                        " and contains(normalize-space(.), '" + itemText + "')]"))
                .click();
    }

    private static void clickButton(String text) {
        waitForEnabled(By.xpath("//button[contains(normalize-space(.), '" + text + "')]")).click();
    }

    private static WebElement waitForDisplayed(By by) {
        var wait = new WebDriverWait(consoleRule.getDriver(), Duration.ofSeconds(60));
        return wait.until(driver -> driver.findElements(by).stream()
                .filter(WebElement::isDisplayed)
                .findFirst()
                .orElse(null));
    }

    private static WebElement waitForEnabled(By by) {
        var wait = new WebDriverWait(consoleRule.getDriver(), Duration.ofSeconds(60));
        return wait.until(driver -> driver.findElements(by).stream()
                .filter(WebElement::isDisplayed)
                .filter(WebElement::isEnabled)
                .findFirst()
                .orElse(null));
    }

    private static void waitForUrlToEndWith(String expectedPath) {
        var wait = new WebDriverWait(consoleRule.getDriver(), Duration.ofSeconds(60));
        wait.until(driver -> driver.getCurrentUrl().endsWith(expectedPath));

        var currentUrl = consoleRule.getDriver().getCurrentUrl();
        assertTrue(currentUrl.endsWith(expectedPath),
                "Expected URL to end with " + expectedPath + ", but was: " + currentUrl);
    }
}
