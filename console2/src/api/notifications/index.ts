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

import { ConcordId, fetchJson, managedFetch } from '../common';

export interface NotificationEntry {
    id: ConcordId;
    userId?: ConcordId;
    orgId?: ConcordId;
    projectId?: ConcordId;
    repoId?: ConcordId;
    summary: string;
    body: string;
    actionLink: string;
    triggerEmail: boolean;
    createdAt: string;
    dismissedTimestamp?: string;
    dismissedBy?: ConcordId;
}

export const listMyNotifications = async (): Promise<NotificationEntry[]> =>
    fetchJson<NotificationEntry[]>('/api/v2/notification');

export const listNotifications = async (
    ownerKind: 'ORG' | 'PROJECT',
    ownerId: ConcordId
): Promise<NotificationEntry[]> =>
    fetchJson<NotificationEntry[]>(
        `/api/v2/notification?ownerKind=${ownerKind}&ownerId=${ownerId}`
    );

export const dismissNotification = async (id: ConcordId): Promise<void> => {
    const resp = await managedFetch(`/api/v2/notification/${id}`, { method: 'DELETE' });
    if (!resp.ok) {
        throw new Error(`Failed to dismiss notification: ${resp.status}`);
    }
};

export interface CreateNotificationRequest {
    userId?: ConcordId;
    orgId?: ConcordId;
    projectId?: ConcordId;
    repoId?: ConcordId;
    summary: string;
    body: string;
    actionLink: string;
    triggerEmail: boolean;
}

export const createNotification = async (req: CreateNotificationRequest): Promise<void> => {
    const resp = await managedFetch('/api/v2/notification', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req),
    });
    if (!resp.ok) {
        throw new Error(`Failed to create notification: ${resp.status}`);
    }
};
