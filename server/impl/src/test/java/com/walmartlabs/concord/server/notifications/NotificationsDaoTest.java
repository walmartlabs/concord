package com.walmartlabs.concord.server.notifications;

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

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("requires a local DB instance")
public class NotificationsDaoTest extends AbstractDaoTest {

    @Test
    public void testInsertAndRetrieve() {
        NotificationsDao dao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();

        UUID id = dao.insert(userId, orgId, projectId, repoId, "Test Summary", "Test Body", "http://example.com", true);

        assertNotNull(id);

        NotificationEntry entry = dao.get(id);
        assertNotNull(entry);
        assertEquals(id, entry.getId());
        assertEquals(userId, entry.getUserId());
        assertEquals(orgId, entry.getOrgId());
        assertEquals(projectId, entry.getProjectId());
        assertEquals(repoId, entry.getRepoId());
        assertEquals("Test Summary", entry.getSummary());
        assertEquals("Test Body", entry.getBody());
        assertEquals("http://example.com", entry.getActionLink());
        assertTrue(entry.isTriggerEmail());
        assertNull(entry.getDismissedTimestamp());
        assertNull(entry.getDismissedBy());
    }

    @Test
    public void testInsertMinimalAndDismiss() {
        NotificationsDao dao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID projectId = UUID.randomUUID();
        UUID dismissedBy = UUID.randomUUID();

        UUID id = dao.insert(null, null, projectId, null, "Summary Only", null, null, false);

        assertNotNull(id);

        OffsetDateTime dismissedTime = OffsetDateTime.now();
        dao.update(id, null, null, null, null, dismissedTime, dismissedBy);

        NotificationEntry entry = dao.get(id);
        assertNotNull(entry);
        assertEquals(id, entry.getId());
        assertNull(entry.getUserId());
        assertNull(entry.getOrgId());
        assertEquals(projectId, entry.getProjectId());
        assertNull(entry.getRepoId());
        assertEquals("Summary Only", entry.getSummary());
        assertNull(entry.getBody());
        assertNull(entry.getActionLink());
        assertFalse(entry.isTriggerEmail());
        assertNotNull(entry.getDismissedTimestamp());
        assertEquals(dismissedBy, entry.getDismissedBy());
    }

    @Test
    public void testUpdateFields() {
        NotificationsDao dao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID projectId = UUID.randomUUID();
        UUID id = dao.insert(null, null, projectId, null, "Original", "Original Body", null, false);

        dao.update(id, "Updated", "Updated Body", "http://new-link.com", true, null, null);

        NotificationEntry entry = dao.get(id);
        assertNotNull(entry);
        assertEquals("Updated", entry.getSummary());
        assertEquals("Updated Body", entry.getBody());
        assertEquals("http://new-link.com", entry.getActionLink());
        assertTrue(entry.isTriggerEmail());
    }

    @Test
    public void testDeleteNotification() {
        NotificationsDao dao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID projectId = UUID.randomUUID();
        UUID id = dao.insert(null, null, projectId, null, "Test", "Body", null, false);

        assertNotNull(dao.get(id));

        dao.delete(id);

        assertNull(dao.get(id));
    }
}
