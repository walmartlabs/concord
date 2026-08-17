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

import * as React from 'react';
import { useCallback, useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { Message } from 'semantic-ui-react';
import { ConcordId, ConcordKey } from '../../../api/common';
import { dismissNotification, listNotifications, NotificationEntry } from '../../../api/notifications';
import { get as getOrg } from '../../../api/org';
import { get as getProject } from '../../../api/org/project';
import NotificationModal from '../NotificationModal';

interface Props {
    orgName?: ConcordKey;
    projectName?: ConcordKey;
}

const POLL_INTERVAL_MS = 60_000;

const NotificationBanners: React.FunctionComponent<Props> = ({ orgName, projectName }) => {
    const location = useLocation();
    const [notifications, setNotifications] = useState<NotificationEntry[]>([]);
    const [selectedId, setSelectedId] = useState<ConcordId | null>(null);
    const [ownerKind, setOwnerKind] = useState<'ORG' | 'PROJECT' | null>(null);
    const [ownerId, setOwnerId] = useState<ConcordId | null>(null);

    // Resolve org/project name → UUID
    useEffect(() => {
        setOwnerId(null);
        setOwnerKind(null);

        if (!orgName) return;

        if (projectName) {
            getProject(orgName, projectName)
                .then((project) => {
                    setOwnerKind('PROJECT');
                    setOwnerId(project.id);
                })
                .catch((e) => console.warn('[NotificationBanners] failed to resolve project:', e));
        } else {
            getOrg(orgName)
                .then((org) => {
                    setOwnerKind('ORG');
                    setOwnerId(org.id);
                })
                .catch((e) => console.warn('[NotificationBanners] failed to resolve org:', e));
        }
    }, [orgName, projectName]);

    const fetchBanners = useCallback(async () => {
        if (!ownerKind || !ownerId) return;
        try {
            const data = await listNotifications(ownerKind, ownerId);
            setNotifications(data.filter((n) => !n.dismissedTimestamp));
        } catch (e) {
            // 403 = user lacks access (expected for non-members); log other errors
            const status = (e as any)?.status;
            if (status !== 403) {
                console.warn('[NotificationBanners] fetch failed:', e);
            }
        }
    }, [ownerKind, ownerId]);

    // Fetch immediately on mount, after ID resolves, and on every route change
    // (covers: initial load, tab switches after creating a notification on the Notify tab)
    useEffect(() => {
        fetchBanners();
    }, [location.pathname, fetchBanners]);

    // Background polling
    useEffect(() => {
        const timer = window.setInterval(fetchBanners, POLL_INTERVAL_MS);
        return () => window.clearInterval(timer);
    }, [fetchBanners]);

    const handleDismiss = useCallback(async (id: ConcordId) => {
        await dismissNotification(id);
        setNotifications((prev) => prev.filter((n) => n.id !== id));
        setSelectedId(null);
    }, []);

    if (notifications.length === 0) return null;

    const selectedNotification = selectedId
        ? notifications.find((n) => n.id === selectedId)
        : undefined;

    return (
        <>
            {notifications.map((n) => (
                <Message
                    key={n.id}
                    info={true}
                    style={{ cursor: 'pointer', marginBottom: '0.5em' }}
                    onClick={() => setSelectedId(n.id)}>
                    <Message.Header>{n.summary}</Message.Header>
                    {n.body && <p>{n.body}</p>}
                </Message>
            ))}
            {selectedNotification && (
                <NotificationModal
                    notification={selectedNotification}
                    onDismiss={handleDismiss}
                    onClose={() => setSelectedId(null)}
                />
            )}
        </>
    );
};

export default NotificationBanners;
