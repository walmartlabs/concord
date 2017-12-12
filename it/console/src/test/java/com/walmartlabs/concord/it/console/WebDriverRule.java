package com.walmartlabs.concord.it.console;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.*;

import java.io.File;
import java.net.URL;
import java.util.logging.Level;

public class WebDriverRule implements TestRule {

    private WebDriver driver;

    public WebDriver getDriver() {
        return driver;
    }

    public boolean isRemote() {
        return "remote".equals(ITConstants.WEBDRIVER_TYPE);
    }

    private void setUp() throws Exception {
        ChromeOptions opts = new ChromeOptions();

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, Level.ALL);
        opts.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);

        opts.addArguments("--dns-prefetch-disable");

        if (isRemote()) {
            URL url = new URL("http://localhost:" + ITConstants.SELENIUM_PORT + "/wd/hub");
            RemoteWebDriver d = new RemoteWebDriver(url, opts);
            d.setFileDetector(new LocalFileDetector());
            driver = new Augmenter().augment(d);
        } else {
            driver = new ChromeDriver();
        }
    }

    private void tearDown() {
        driver.quit();
    }

    private void takeScreenshot(Description d) {
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        String fileName = d.getTestClass().getName() + "-" + d.getMethodName() + ".png";

        File dstDir = new File(ITConstants.SCREENSHOTS_DIR);
        if (!dstDir.exists()) {
            dstDir.mkdirs();
        }

        File dst = new File(dstDir, fileName);
        src.renameTo(dst);

        System.out.println("Screenshot saved to: " + dst.getAbsolutePath());
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                setUp();
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    takeScreenshot(description);
                    throw t;
                } finally {
                    tearDown();
                }
            }
        };
    }
}
