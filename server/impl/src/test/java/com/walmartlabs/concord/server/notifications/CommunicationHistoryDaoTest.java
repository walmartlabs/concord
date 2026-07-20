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
public class CommunicationHistoryDaoTest extends AbstractDaoTest {

    @Test
    public void testInsertAndRetrieve() {
        CommunicationHistoryDao dao = new CommunicationHistoryDao(getConfiguration(), getUuidGenerator());
        NotificationsDao notificationsDao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID notificationId = notificationsDao.insert(null, null, UUID.randomUUID(), null, "Test", "Body", null, false);

        OffsetDateTime timestamp = OffsetDateTime.now();
        UUID id = dao.insert(notificationId, "user@example.com", "cc@example.com", "bcc@example.com", timestamp);

        assertNotNull(id);

        CommunicationHistoryEntry entry = dao.get(id);
        assertNotNull(entry);
        assertEquals(id, entry.getId());
        assertEquals(notificationId, entry.getNotificationId());
        assertEquals("user@example.com", entry.getEmailTo());
        assertEquals("cc@example.com", entry.getEmailCc());
        assertEquals("bcc@example.com", entry.getEmailBcc());
        assertNotNull(entry.getEmailTimestamp());
    }

    @Test
    public void testInsertMinimalAndUpdate() {
        CommunicationHistoryDao dao = new CommunicationHistoryDao(getConfiguration(), getUuidGenerator());
        NotificationsDao notificationsDao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID notificationId = notificationsDao.insert(null, null, UUID.randomUUID(), null, "Test", "Body", null, false);

        OffsetDateTime timestamp = OffsetDateTime.now();
        UUID id = dao.insert(notificationId, "original@example.com", null, null, timestamp);

        dao.update(id, "updated@example.com", "newcc@example.com", "bcc@example.com");

        CommunicationHistoryEntry entry = dao.get(id);
        assertNotNull(entry);
        assertEquals(id, entry.getId());
        assertEquals(notificationId, entry.getNotificationId());
        assertEquals("updated@example.com", entry.getEmailTo());
        assertEquals("newcc@example.com", entry.getEmailCc());
        assertEquals("bcc@example.com", entry.getEmailBcc());
    }

    @Test
    public void testPartialUpdate() {
        CommunicationHistoryDao dao = new CommunicationHistoryDao(getConfiguration(), getUuidGenerator());
        NotificationsDao notificationsDao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID notificationId = notificationsDao.insert(null, null, UUID.randomUUID(), null, "Test", "Body", null, false);

        OffsetDateTime timestamp = OffsetDateTime.now();
        UUID id = dao.insert(notificationId, "original@example.com", "cc@example.com", null, timestamp);

        dao.update(id, "updated@example.com", null, null);

        CommunicationHistoryEntry entry = dao.get(id);
        assertNotNull(entry);
        assertEquals("updated@example.com", entry.getEmailTo());
        assertEquals("cc@example.com", entry.getEmailCc());
        assertNull(entry.getEmailBcc());
    }

    @Test
    public void testDeleteCommunicationHistory() {
        CommunicationHistoryDao dao = new CommunicationHistoryDao(getConfiguration(), getUuidGenerator());
        NotificationsDao notificationsDao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID notificationId = notificationsDao.insert(null, null, UUID.randomUUID(), null, "Test", "Body", null, false);

        OffsetDateTime timestamp = OffsetDateTime.now();
        UUID id = dao.insert(notificationId, "user@example.com", null, null, timestamp);

        assertNotNull(dao.get(id));

        dao.delete(id);

        assertNull(dao.get(id));
    }

    @Test
    public void testMultipleEntriesForSameNotification() {
        CommunicationHistoryDao dao = new CommunicationHistoryDao(getConfiguration(), getUuidGenerator());
        NotificationsDao notificationsDao = new NotificationsDao(getConfiguration(), getUuidGenerator());

        UUID notificationId = notificationsDao.insert(null, null, UUID.randomUUID(), null, "Test", "Body", null, false);

        OffsetDateTime timestamp1 = OffsetDateTime.now();
        OffsetDateTime timestamp2 = OffsetDateTime.now().plusSeconds(1);

        UUID id1 = dao.insert(notificationId, "user1@example.com", null, null, timestamp1);
        UUID id2 = dao.insert(notificationId, "user2@example.com", "cc@example.com", null, timestamp2);

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);

        CommunicationHistoryEntry entry1 = dao.get(id1);
        CommunicationHistoryEntry entry2 = dao.get(id2);

        assertNotNull(entry1);
        assertNotNull(entry2);
        assertEquals(notificationId, entry1.getNotificationId());
        assertEquals(notificationId, entry2.getNotificationId());
        assertEquals("user1@example.com", entry1.getEmailTo());
        assertEquals("user2@example.com", entry2.getEmailTo());
    }
}
