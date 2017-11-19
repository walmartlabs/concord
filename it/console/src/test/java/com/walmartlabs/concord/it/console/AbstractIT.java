package com.walmartlabs.concord.it.console;

import com.walmartlabs.concord.it.common.ServerClient;
import org.junit.After;
import org.junit.Before;
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class AbstractIT {

    private static final long DEFAULT_WAIT_TIME = 10; // seconds

    @Rule
    public final WebDriverRule rule = new WebDriverRule();

    private ServerClient serverClient;

    protected WebDriver getDriver() {
        return rule.getDriver();
    }

    @Before
    public void init() {
        serverClient = new ServerClient(ITConstants.SERVER_URL);
    }

    @After
    public void _destroy() {
        serverClient.close();
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

    protected <T> T proxy(Class<T> klass) {
        return serverClient.proxy(klass);
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
