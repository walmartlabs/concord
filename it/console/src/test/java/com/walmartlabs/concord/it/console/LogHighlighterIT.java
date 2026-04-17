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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class LogHighlighterIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testAnsiColorRendering() throws Exception {
        var process = startProcess("logHighlighter");
        assertEquals(ProcessEntry.StatusEnum.FINISHED, process.getStatus());

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.executeJavaScript("localStorage.setItem('logOptsV2', '{\"expandAllSegments\":true,\"showSystemSegment\":true,\"segmentOptions\":{\"useLocalTime\":true,\"showDate\":false,\"separateTasks\":true}}')");
        consoleRule.navigateToRelative("/#/process/" + process.getInstanceId() + "/log");
        consoleRule.waitForLoad();

        var redText = consoleRule.waitFor(By.xpath("//pre//span[contains(., 'ANSI_RED')]"));
        assertNotNull(redText);
        assertEquals("rgba(187, 0, 0, 1)", redText.getCssValue("color"));
    }

    private ProcessEntry startProcess(String resource) throws Exception {
        byte[] payload = archive(LogHighlighterIT.class.getResource(resource).toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = serverRule.start(input);
        assertNotNull(spr.getInstanceId());

        return waitForCompletion(serverRule.getClient(), spr.getInstanceId());
    }
}
