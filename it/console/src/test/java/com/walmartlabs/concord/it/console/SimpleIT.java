package com.walmartlabs.concord.it.console;

import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.By;

@Ignore
public class SimpleIT extends AbstractIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        start();
        waitForPage();
        waitFor("Logo should be visible", By.id("logo"));
        assertNoErrors();
    }
}
