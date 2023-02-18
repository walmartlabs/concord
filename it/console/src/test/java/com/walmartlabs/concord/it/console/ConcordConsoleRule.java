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

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import static com.walmartlabs.concord.it.console.Utils.env;

public class ConcordConsoleRule extends WebDriverRule {

    private static final Logger log = LoggerFactory.getLogger(ConcordConsoleRule.class);

    private final String baseUrl;

    public ConcordConsoleRule() {
        this.baseUrl = env("IT_CONSOLE_BASE_URL", "http://localhost:3000");
        log.info("Using baseUrl: {}", baseUrl);
    }

    public WebDriver navigateToRelative(String path) throws URISyntaxException {
        WebDriver driver = getDriver();
        URI uri = new URI(driver.getCurrentUrl()).resolve(path);
        driver.get(uri.toString());
        return driver;
    }

    public void login(String apiKey) throws Exception {
        WebDriver driver = navigateToRelative("/#/login?useApiKey=true");

        WebElement apiKeyField = driver.findElement(By.name("apiKey"));
        apiKeyField.sendKeys(apiKey);

        WebElement loginButton = driver.findElement(By.id("loginButton"));
        loginButton.click();

        waitForLoad();
    }

    public Object executeJavaScript(String js) {
        WebDriver driver = getDriver();
        JavascriptExecutor exec = (JavascriptExecutor) driver;
        return exec.executeScript(js);
    }

    public WebElement waitFor(By by) {
        ExpectedCondition<Boolean> condition = d -> {
            if (d == null) {
                return false;
            }

            try {
                d.findElement(by);
            } catch (NoSuchElementException e) {
                return false;
            }

            return true;
        };

        WebDriver driver = getDriver();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        wait.until(condition);

        return driver.findElement(by);
    }

    public void waitForLoad() throws InterruptedException {
        ExpectedCondition<Boolean> expectation = driver -> executeJavaScript("return document.readyState")
                .toString()
                .equals("complete");

        Thread.sleep(500);

        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(30));
        wait.until(expectation);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        WebDriver driver = getDriver();
        driver.get(baseUrl);
    }
}
