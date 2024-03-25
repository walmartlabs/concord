package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessCardAccessEntry;
import com.walmartlabs.concord.client2.ProcessCardEntry;
import com.walmartlabs.concord.client2.ProcessCardsApi;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ProcessCardIT extends AbstractServerIT {

    @Test
    public void testInvalidRequests() throws Exception {
        ProcessCardsApi api = new ProcessCardsApi(getApiClient());

        // no cards
        List<ProcessCardEntry> cards = api.listUserProcessCards();
        assertNotNull(cards);
        assertTrue(cards.isEmpty());

        // unknown card id -> 404 for form
        try {
            api.getProcessCardForm(UUID.randomUUID());
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }

        // unknown card id -> 404 for form data
        try {
            api.getProcessCardFormData(UUID.randomUUID());
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }

        // unknown card id -> 404 for delete card
        try {
            api.deleteProcessCard(UUID.randomUUID());
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }
    }

    @Test
    public void testCRUD() throws Exception {
        ProcessCardsApi api = new ProcessCardsApi(getApiClient());

        withOrg(orgName -> {
            withProject(orgName, projectName -> {
                var r = new ProcessCardsApi.CreateOrUpdateProcessCardRequest()
                        .org(orgName)
                        .project(projectName)
                        .name("test")
                        .description("test description")
                        .data(Map.of("myKey", "myValue"))
                        .form("hello world")
                        .icon("test icon");

                // create card
                UUID cardId = api.createOrUpdateProcessCard(r.asMap()).getId();

                ProcessCardEntry entry = api.getProcessCard(cardId);
                assertEquals(orgName, entry.getOrgName());
                assertEquals(projectName, entry.getProjectName());
                assertEquals("test", entry.getName());
                assertEquals("test description", entry.getDescription());

                // -- list cards
                List<ProcessCardEntry> cards = api.listUserProcessCards();
                assertEquals(1, cards.size());

                try (InputStream is = api.getProcessCardForm(cardId)) {
                    assertEquals("hello world", new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }

                try (InputStream is = api.getProcessCardFormData(cardId)) {
                    String data = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(data.startsWith("data = "), data);
                    assertTrue(data.contains("myKey"), data);
                    assertTrue(data.contains("myValue"), data);
                }

                // update card
                r = new ProcessCardsApi.CreateOrUpdateProcessCardRequest()
                        .id(cardId)
                        .org(orgName)
                        .project(projectName)
                        .name("test update")
                        .description("test description update");

                UUID cardIdAfterUpdate = api.createOrUpdateProcessCard(r.asMap()).getId();
                assertEquals(cardId, cardIdAfterUpdate);

                entry = api.getProcessCard(cardId);
                assertEquals(orgName, entry.getOrgName());
                assertEquals(projectName, entry.getProjectName());
                assertEquals("test update", entry.getName());
                assertEquals("test description update", entry.getDescription());

                // delete card
                api.deleteProcessCard(cardId);
            });
        });
    }
}
