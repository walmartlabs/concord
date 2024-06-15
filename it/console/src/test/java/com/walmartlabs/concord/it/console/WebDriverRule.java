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

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import static com.walmartlabs.concord.it.console.Utils.env;

public class WebDriverRule implements BeforeEachCallback, TestExecutionExceptionHandler, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger(WebDriverRule.class);

    private final int seleniumPort;
    private final String driverType;
    private final String screenshotsDir;

    private WebDriver driver;

    public WebDriverRule() {
        this.seleniumPort = Integer.parseInt(env("IT_SELENIUM_PORT", "4444"));
        this.driverType = env("IT_WEBDRIVER_TYPE", "local");
        this.screenshotsDir = env("IT_SCREENSHOTS_DIR", "target/screenshots");
    }

    public WebDriver getDriver() {
        return driver;
    }

    public boolean isRemote() {
        return "remote".equals(driverType);
    }

    protected void setUp() throws Exception {
        ChromeOptions opts = new ChromeOptions();

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, Level.ALL);
        opts.setCapability("goog:loggingPrefs", logPrefs);

        opts.addArguments("--dns-prefetch-disable");

        if (isRemote()) {
            URL url = new URL("http://localhost:" + seleniumPort + "/wd/hub");
            log.info("Using a remote driver: {}", url);
            RemoteWebDriver d = new RemoteWebDriver(url, opts);
            d.setFileDetector(new LocalFileDetector());
            driver = new Augmenter().augment(d);
        } else {
            log.info("Using a local driver...");
            driver = new ChromeDriver();
        }
    }

    private void tearDown() {
        driver.quit();
    }

    private void takeScreenshot(ExtensionContext context) throws IOException {
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

        Path dstDir = Paths.get(screenshotsDir);
        Files.createDirectories(dstDir);

        String fileName = context.getTestClass().get().getName() + context.getTestMethod().get().getName() + ".png";
        Path dst = dstDir.resolve(fileName);

        Files.copy(src.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Screenshot saved to: " + dst);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        setUp();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        tearDown();
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        takeScreenshot(context);
        throw throwable;
    }
}
