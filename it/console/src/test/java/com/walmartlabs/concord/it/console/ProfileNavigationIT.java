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

import com.walmartlabs.concord.it.common.ITUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class ProfileNavigationIT {

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testTopBarDropdownNavigation() throws Exception {
        consoleRule.login(Concord.ADMIN_API_KEY);

        clickable(By.cssSelector("[data-testid='topbar-system-menu']")).click();
        clickable(By.cssSelector("[data-testid='topbar-about']")).click();

        consoleRule.waitFor(By.xpath("//*[contains(text(), 'Server version:')]"));
        assertTrue(consoleRule.getDriver().getCurrentUrl().contains("/#/about"));

        clickable(By.cssSelector("[data-testid='topbar-user-menu']")).click();
        clickable(By.cssSelector("[data-testid='topbar-profile']")).click();

        consoleRule.waitFor(By.xpath("//h4[contains(text(), 'API Tokens')]"));
        assertTrue(consoleRule.getDriver().getCurrentUrl().contains("/#/profile/api-token"));
    }

    @Test
    public void testApiTokenDoneNavigation() throws Exception {
        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/profile/api-token");

        clickable(By.cssSelector("[data-testid='api-token-new-button']")).click();
        consoleRule.waitFor(By.cssSelector("[data-testid='api-token-form-name'] input"))
                .sendKeys("token_" + ITUtils.randomString());

        clickable(By.cssSelector("[data-testid='api-token-form-submit']")).click();
        clickable(By.cssSelector("[data-testid='api-token-created-done']")).click();

        consoleRule.waitFor(By.xpath("//h4[contains(text(), 'API Tokens')]"));

        String currentUrl = consoleRule.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("/#/profile/api-token"));
        assertFalse(currentUrl.contains("_new"));
    }

    @Test
    public void testDirectNavigationRedirects() throws Exception {
        consoleRule.login(Concord.ADMIN_API_KEY);

        consoleRule.navigateToRelative("/#/");
        waitForUrlContaining("/#/activity");
        consoleRule.waitFor(By.id("concordLogo"));

        consoleRule.navigateToRelative("/#/profile");
        waitForUrlContaining("/#/profile/api-token");
        consoleRule.waitFor(By.xpath("//h4[contains(text(), 'API Tokens')]"));
    }

    @Test
    public void testProtectedRouteRedirectsToLogin() throws Exception {
        consoleRule.navigateToRelative("/#/profile/api-token");

        waitForUrlContaining("/#/login");
        consoleRule.waitFor(By.id("loginButton"));
    }

    private static WebElement clickable(By by) {
        return new WebDriverWait(consoleRule.getDriver(), Duration.ofSeconds(30))
                .until(ExpectedConditions.elementToBeClickable(by));
    }

    private static void waitForUrlContaining(String value) {
        new WebDriverWait(consoleRule.getDriver(), Duration.ofSeconds(30))
                .until(ExpectedConditions.urlContains(value));
    }
}
