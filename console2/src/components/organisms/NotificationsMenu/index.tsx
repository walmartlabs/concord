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
import { Dropdown, Label } from 'semantic-ui-react';
import {
    dismissNotification,
    listMyNotifications,
    NotificationEntry
} from '../../../api/notifications';
import { ConcordId } from '../../../api/common';
import NotificationModal from '../NotificationModal';

const STORAGE_KEY_PREFIX = 'concord_notifications_viewed_';
const POLL_INTERVAL_MS = 60_000;

const getViewedKey = (username: string) => `${STORAGE_KEY_PREFIX}${username}`;

const loadViewedIds = (username: string): Set<ConcordId> => {
    try {
        const raw = localStorage.getItem(getViewedKey(username));
        return raw ? new Set<ConcordId>(JSON.parse(raw)) : new Set<ConcordId>();
    } catch {
        return new Set<ConcordId>();
    }
};

const saveViewedIds = (username: string, ids: Set<ConcordId>) => {
    try {
        localStorage.setItem(getViewedKey(username), JSON.stringify([...ids]));
    } catch {
        // ignore storage quota errors
    }
};

interface Props {
    username: string;
}

const NotificationsMenu: React.FunctionComponent<Props> = ({ username }) => {
    const [notifications, setNotifications] = React.useState<NotificationEntry[]>([]);
    const [viewedIds, setViewedIds] = React.useState<Set<ConcordId>>(() =>
        loadViewedIds(username)
    );
    const [open, setOpen] = React.useState(false);
    const [selectedId, setSelectedId] = React.useState<ConcordId | null>(null);

    const fetchNotifications = React.useCallback(async () => {
        try {
            const data = await listMyNotifications();
            const active = data.filter((n) => !n.dismissedTimestamp);
            setNotifications(active);

            // Prune stored viewed IDs against the current active set
            const activeIds = new Set(active.map((n) => n.id));
            setViewedIds((prev) => {
                const pruned = new Set<ConcordId>([...prev].filter((id) => activeIds.has(id)));
                if (pruned.size !== prev.size) {
                    saveViewedIds(username, pruned);
                }
                return pruned;
            });
        } catch {
            // Silently ignore poll failures
        }
    }, [username]);

    React.useEffect(() => {
        fetchNotifications();
        const timer = window.setInterval(fetchNotifications, POLL_INTERVAL_MS);
        return () => window.clearInterval(timer);
    }, [fetchNotifications]);

    const markAllViewed = (current: NotificationEntry[]) => {
        const updated = new Set<ConcordId>([...viewedIds, ...current.map((n) => n.id)]);
        setViewedIds(updated);
        saveViewedIds(username, updated);
    };

    const handleOpen = () => {
        setOpen(true);
        markAllViewed(notifications);
    };

    const handleClose = () => setOpen(false);

    const handleDismiss = async (id: ConcordId) => {
        await dismissNotification(id);
        setNotifications((prev) => prev.filter((n) => n.id !== id));
        setViewedIds((prev) => {
            const updated = new Set<ConcordId>(prev);
            updated.delete(id);
            saveViewedIds(username, updated);
            return updated;
        });
        setSelectedId(null);
    };

    const hasNotifications = notifications.length > 0;
    const freshCount = notifications.filter((n) => !viewedIds.has(n.id)).length;
    const selectedNotification = selectedId
        ? notifications.find((n) => n.id === selectedId)
        : undefined;

    const trigger = (
        <span style={{ color: hasNotifications ? 'white' : 'rgba(255,255,255,0.45)' }}>
            Notifications
            {freshCount > 0 && (
                <Label
                    color="red"
                    size="mini"
                    circular={true}
                    style={{ marginLeft: '0.5em', verticalAlign: 'middle' }}>
                    {freshCount}
                </Label>
            )}
        </span>
    );

    return (
        <>
            <Dropdown
                item={true}
                trigger={trigger}
                disabled={!hasNotifications}
                open={open}
                onOpen={handleOpen}
                onClose={handleClose}
                pointing="top right">
                <Dropdown.Menu>
                    {notifications.map((n) => (
                        <Dropdown.Item
                            key={n.id}
                            text={n.summary}
                            onClick={() => {
                                setSelectedId(n.id);
                                setOpen(false);
                            }}
                        />
                    ))}
                </Dropdown.Menu>
            </Dropdown>

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

export default NotificationsMenu;
