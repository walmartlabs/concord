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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class FormsIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testDateTimeField() throws Exception {
        ApiClient apiClient = serverRule.getClient();

        // ---

        byte[] payload = archive(FormsIT.class.getResource("dateTimeField").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        ProcessApi processApi = new ProcessApi(apiClient);
        StartProcessResponse spr = serverRule.start(input);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

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

        pir = waitForCompletion(processApi, pir.getInstanceId());

        byte[] ab = serverRule.getLog(pir.getLogFileName());
        assertLog(".*dateField=2019-09-04.*", ab);
        assertLog(".*dateTimeField=2019-09-04T05:05:00.000Z.*", ab);
    }
}
