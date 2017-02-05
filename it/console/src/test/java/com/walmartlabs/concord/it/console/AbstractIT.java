package com.walmartlabs.concord.it.console;

import org.junit.Rule;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static org.junit.Assert.*;

public abstract class AbstractIT {

    private static final long DEFAULT_WAIT_TIME = 10; // seconds

    @Rule
    public WebDriverRule rule = new WebDriverRule();

    protected WebDriver getDriver() {
        return rule.getDriver();
    }

    protected void start() {
        int port = ITConstants.LOCAL_CONSOLE_PORT;
        if (rule.isRemote()) {
            port = ITConstants.REMOTE_CONSOLE_PORT;
        }
        getDriver().get("http://localhost:" + port);
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

    protected void click(String message, By selector) {
        WebElement e = get(message, selector);
        e.click();
    }

    protected void waitAndClick(String message, By selector) {
        WebElement e = waitFor(message, selector);
        e.click();
    }

    protected void assertElement(String message, By selector) {
        assertFalse(message, getDriver().findElements(selector).isEmpty());
    }

    protected void assertNoElements(String message, By selector) {
        assertTrue(message, getDriver().findElements(selector).isEmpty());
    }
}
