package com.walmartlabs.concord.it.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.Assert.fail;

public abstract class AbstractIT {

    private static final long DEFAULT_WAIT_TIME = 10; // seconds

    @Rule
    public final WebDriverRule rule = new WebDriverRule();

    protected WebDriver getDriver() {
        return rule.getDriver();
    }

    protected void start() {
        goTo("/");
    }

    protected void goTo(String path) {
        getDriver().get("http://localhost:" + getConsolePort() + path);
    }

    protected int getConsolePort() {
        if (rule.isRemote()) {
            return ITConstants.REMOTE_CONSOLE_PORT;
        }
        return ITConstants.LOCAL_CONSOLE_PORT;
    }

    protected void waitForPage() {
        getDriver().manage().timeouts().pageLoadTimeout(10, TimeUnit.SECONDS);
    }

    protected Alert waitForAlert(String message) {
        try {
            WebDriverWait wait = new WebDriverWait(getDriver(), DEFAULT_WAIT_TIME);
            return wait.until(ExpectedConditions.alertIsPresent());
        } catch (Exception e) {
            fail(message);
            return null;
        }
    }

    protected WebElement waitFor(String message, By selector) {
        return waitFor(message, DEFAULT_WAIT_TIME, selector);
    }

    protected WebElement waitFor(String message, long timeout, By selector) {
        try {
            WebDriverWait wait = new WebDriverWait(getDriver(), timeout);
            return wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
        } catch (Exception e) {
            fail(message);
            return null;
        }
    }

    protected WebElement get(String message, By selector) {
        try {
            return getDriver().findElement(selector);
        } catch (Exception e) {
            fail(message);
            return null;
        }
    }

    protected void assertNoErrors() {
        Logs logs = getDriver().manage().logs();
        List<LogEntry> errors = logs.get(LogType.BROWSER).filter(Level.SEVERE);
        if (!errors.isEmpty()) {
            for (LogEntry e : errors) {
                System.err.print(">>> ");
                System.err.println(e.getMessage());
            }
            fail("Found some errors in the log");
        }
    }
}
