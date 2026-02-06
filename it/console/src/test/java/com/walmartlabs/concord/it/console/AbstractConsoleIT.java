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

import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.it.common.JGitUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.walmartlabs.concord.it.console.ITConstants.DEFAULT_TEST_TIMEOUT;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
@ExtendWith({SharedConcordExtension.class, AbstractConsoleIT.ScreenshotExtension.class})
public abstract class AbstractConsoleIT {

    private static final Logger log = LoggerFactory.getLogger(AbstractConsoleIT.class);

    private static ConcordRule concord;

    private ApiClient apiClient;
    private WebDriver driver;

    @BeforeAll
    public static void _initConcord(ConcordRule rule) {
        JGitUtils.applyWorkarounds();
        concord = rule;
    }

    @BeforeEach
    public void _setUp() throws Exception {
        this.apiClient = new DefaultApiClientFactory(concord.apiBaseUrl())
                .create(ApiClientConfiguration.builder().apiKey(concord.environment().apiToken()).build());

        ChromeOptions opts = new ChromeOptions();
        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, Level.ALL);
        opts.setCapability("goog:loggingPrefs", logPrefs);
        opts.addArguments("--dns-prefetch-disable");

        URL url = new URL("http://localhost:" + ConcordConfiguration.seleniumPort() + "/wd/hub");
        log.info("Using a remote driver: {}", url);
        RemoteWebDriver d = new RemoteWebDriver(url, opts);
        d.setFileDetector(new LocalFileDetector());
        this.driver = d;

        driver.get(ConcordConfiguration.consoleBaseUrl());
    }

    @AfterEach
    public void _tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    // --- server-side helpers

    protected static ConcordRule concord() {
        return concord;
    }

    protected static String defaultApiKey() {
        return concord.environment().apiToken();
    }

    protected ApiClient getApiClient() {
        return apiClient;
    }

    protected StartProcessResponse start(Map<String, Object> input) throws ApiException {
        return new ProcessApi(apiClient).startProcess(input);
    }

    protected byte[] getLog(UUID instanceId) throws ApiException {
        try (InputStream is = new ProcessApi(apiClient).getProcessLog(instanceId, null)) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String createRepo(String resource) throws Exception {
        Path tmpDir = Files.createTempDirectory(ConcordConfiguration.sharedDir(), "test");
        Files.setPosixFilePermissions(tmpDir, PosixFilePermissions.fromString("rwxr-xr-x"));

        File src = new File(getClass().getResource(resource).toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        return tmpDir.toAbsolutePath().toString();
    }

    // --- console/WebDriver helpers

    protected WebDriver getDriver() {
        return driver;
    }

    protected void login(String apiKey) throws Exception {
        WebDriver d = navigateToRelative("/#/login?useApiKey=true");

        WebElement apiKeyField = d.findElement(By.name("apiKey"));
        apiKeyField.sendKeys(apiKey);

        WebElement loginButton = d.findElement(By.id("loginButton"));
        loginButton.click();

        waitForLoad();
    }

    protected WebDriver navigateToRelative(String path) throws URISyntaxException {
        URI uri = new URI(driver.getCurrentUrl()).resolve(path);
        driver.get(uri.toString());
        return driver;
    }

    protected WebElement waitFor(By by) {
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

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        wait.until(condition);

        return driver.findElement(by);
    }

    protected void waitForLoad() throws InterruptedException {
        ExpectedCondition<Boolean> expectation = d -> executeJavaScript("return document.readyState")
                .toString()
                .equals("complete");

        Thread.sleep(500);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(expectation);
    }

    protected Object executeJavaScript(String js) {
        JavascriptExecutor exec = (JavascriptExecutor) driver;
        return exec.executeScript(js);
    }

    // --- screenshot extension

    public static class ScreenshotExtension implements TestExecutionExceptionHandler {

        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
            Object testInstance = context.getTestInstance().orElse(null);
            if (testInstance instanceof AbstractConsoleIT it && it.driver != null) {
                try {
                    File src = ((TakesScreenshot) it.driver).getScreenshotAs(OutputType.FILE);
                    Path dstDir = Paths.get("target/screenshots");
                    Files.createDirectories(dstDir);
                    String fileName = context.getTestClass().get().getName() + context.getTestMethod().get().getName() + ".png";
                    Path dst = dstDir.resolve(fileName);
                    Files.copy(src.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Screenshot saved to: " + dst);
                } catch (Exception e) {
                    System.err.println("Failed to take screenshot: " + e.getMessage());
                }
            }
            throw throwable;
        }
    }
}
