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

import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.interactions.Actions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class ProcessAnsibleIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testAnsibleTabSmoke() throws Exception {
        var process = startProcess("processAnsible");
        assertEquals(ProcessEntry.StatusEnum.FINISHED, process.getStatus());
        var processUrl = "/#/process/" + process.getInstanceId() + "/ansible";

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative(processUrl);
        consoleRule.waitForLoad();

        var uniqueHostsCard = consoleRule.waitFor(By.xpath("//div[normalize-space()='UNIQUE HOSTS']"));
        assertNotNull(uniqueHostsCard);

        uniqueHostsCard.click();
        consoleRule.waitFor(By.xpath("//div[normalize-space()='Host Stats']"));

        var hostRow = consoleRule.waitFor(By.xpath("//table//tr[.//td[normalize-space()='127.0.0.1']]"));
        hostRow.click();

        consoleRule.waitFor(By.xpath("//h3[normalize-space()='127.0.0.1']"));
        consoleRule.waitFor(By.xpath("//td[normalize-space()='Announce host']"));
        consoleRule.waitFor(By.xpath("//td[normalize-space()='Verify host']"));

        closeActiveModal();

        consoleRule.navigateToRelative(processUrl);
        consoleRule.waitForLoad();

        var playsCard = consoleRule.waitFor(By.xpath("//div[normalize-space()='PLAYS']"));
        playsCard.click();

        consoleRule.waitFor(By.xpath("//div[normalize-space()='Play Stats']"));

        var playRow = consoleRule.waitFor(By.xpath("//table//tr[.//td[normalize-space()='Smoke play']]"));
        playRow.click();

        consoleRule.waitFor(By.xpath("//div[normalize-space()='Task Stats']"));
        consoleRule.waitFor(By.xpath("//td[normalize-space()='Announce host']"));
        consoleRule.waitFor(By.xpath("//td[normalize-space()='Verify host']"));

    }

    private ProcessEntry startProcess(String resource) throws Exception {
        byte[] payload = archive(ProcessAnsibleIT.class.getResource(resource).toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = serverRule.start(input);
        assertNotNull(spr.getInstanceId());

        return waitForCompletion(serverRule.getClient(), spr.getInstanceId());
    }

    private void closeActiveModal() throws Exception {
        new Actions(consoleRule.getDriver())
                .sendKeys(Keys.ESCAPE)
                .perform();

        waitForNoElements(By.cssSelector(".ui.fullscreen.modal.active"));
    }

    private void waitForNoElements(By by) throws Exception {
        for (var attempt = 0; attempt < 20; attempt++) {
            if (consoleRule.getDriver().findElements(by).isEmpty()) {
                return;
            }

            Thread.sleep(250);
        }

        fail("Timed out waiting for element to disappear: " + by);
    }
}
