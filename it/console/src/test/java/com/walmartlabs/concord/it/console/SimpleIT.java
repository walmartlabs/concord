package com.walmartlabs.concord.it.console;

import org.junit.Test;
import org.openqa.selenium.By;

public class SimpleIT extends AbstractIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        start();
        waitForPage();
        waitFor("Logo should be visible", By.id("logo"));
        assertNoErrors();
    }
}
