package com.walmartlabs.concord.it.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
class FormsIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    void testDateTimeField() throws Exception {
        ProcessEntry pir = startConsoleProcess("dateTimeField");

        // ---

        consoleRule.login(Concord.ADMIN_API_KEY);

        String url = "/#/process/" + pir.getInstanceId();
        consoleRule.navigateToRelative(url);

        WebElement wizardButton = consoleRule.waitFor(By.id("formWizardButton"));
        wizardButton.click();

        WebElement dateField = consoleRule.waitFor(By.name("dateField"));
        dateField.sendKeys("2019-09-04");

        WebElement dateTimeField = consoleRule.waitFor(By.name("dateTimeField"));
        dateTimeField.sendKeys("2019-09-04T01:05:00.000-04:00");

        // close the popup
        consoleRule.waitFor(By.id("root")).click();

        Thread.sleep(1000);

        WebElement submitButton = consoleRule.waitFor(By.id("formSubmitButton"));
        submitButton.click();

        // ---

        pir = waitForCompletion(serverRule.getClient(), pir.getInstanceId());

        byte[] ab = serverRule.getLog(pir.getInstanceId());
        assertLog(".*dateField=2019-09-04.*", ab);
        assertLog(".*dateTimeField=2019-09-04T05:05:00.000Z.*", ab);
    }

    @Test
    void testStringValues() throws Exception {
        ProcessEntry pir = startConsoleProcess("stringValues");

        // ---

        consoleRule.login(Concord.ADMIN_API_KEY);

        String url = "/#/process/" + pir.getInstanceId();
        consoleRule.navigateToRelative(url);

        WebElement wizardButton = consoleRule.waitFor(By.id("formWizardButton"));
        wizardButton.click();

        consoleRule.waitFor(By.name("field0")).click();
        new Actions(consoleRule.getDriver()) // first option is selected on open
                .sendKeys(Keys.DOWN)
                .sendKeys(Keys.ENTER) // select "second" value
                .sendKeys(Keys.ENTER) // select "third" value
                .sendKeys(Keys.ESCAPE)
                .perform();

        consoleRule.waitFor(By.name("field1")).click();
        new Actions(consoleRule.getDriver())
                .sendKeys(Keys.ENTER) // select "third" value
                .sendKeys(Keys.ESCAPE)
                .perform();

        consoleRule.waitFor(By.name("field2")).click();
        new Actions(consoleRule.getDriver())
                .sendKeys(Keys.BACK_SPACE) // remove "second" value
                .sendKeys("third")         // add "third" value
                .sendKeys(Keys.ENTER)
                .sendKeys("fourth")        // add "fourth" value
                .sendKeys(Keys.ENTER)
                .sendKeys(Keys.ESCAPE)
                .perform();

        consoleRule.waitFor(By.name("field3")).click();
        new Actions(consoleRule.getDriver())
                .sendKeys("hello")    // add two strings
                .sendKeys(Keys.ENTER)
                .sendKeys("world")
                .sendKeys(Keys.ENTER)
                .sendKeys(Keys.ESCAPE)
                .perform();

        consoleRule.waitFor(By.name("field4")).click();
        new Actions(consoleRule.getDriver())
                .sendKeys("single string")
                .perform();

        WebElement submitButton = consoleRule.waitFor(By.id("formSubmitButton"));
        submitButton.click();

        // ---

        pir = waitForCompletion(serverRule.getClient(), pir.getInstanceId());

        byte[] ab = serverRule.getLog(pir.getInstanceId());
        assertLog(".*\"field0\" : \\[ \"second\", \"third\" ].*", ab);
        assertLog(".*\"field1\" : \\[ \"first\", \"second\", \"third\" ].*", ab);
        assertLog(".*\"field2\" : \\[ \"first\", \"third\", \"fourth\" ].*", ab);
        assertLog(".*\"field3\" : \\[ \"hello\", \"world\" ].*", ab);
        assertLog(".*\"field4\" : \"single string\".*", ab);
    }

    private ProcessEntry startConsoleProcess(String res) throws Exception {
        byte[] payload = archive(FormsIT.class.getResource(res).toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = serverRule.start(input);
        assertNotNull(spr.getInstanceId());

        return waitForStatus(serverRule.getClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
    }
}
