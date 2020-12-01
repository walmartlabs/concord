/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import { useCallback, useState } from 'react';
import ReactJson from 'react-json-view';
import { Button, Divider, Header, Modal, Popup, Table } from 'semantic-ui-react';
import { add, format, parseISO, sub } from 'date-fns';

import { ProcessEntry } from '../../../api/process';
import { AuditLogEntry, AuditObject, list as apiList } from '../../../api/audit';
import { RequestError } from '../../../api/common';
import { WithCopyToClipboard } from '../../molecules';
import { RequestErrorActivity } from "../index";

// TODO move into common constants?
const DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

interface Props {
    entry: ProcessEntry;
}

const getIcon = (type?: string) => {
    switch (type) {
        case 'github':
            return 'github';
        case 'cron':
            return 'clock outline';
        default:
            return 'question';
    }
};

const getTitle = (type?: string) => {
    if (!type) {
        return 'Unknown';
    }

    switch (type) {
        case 'github':
            return 'GitHub';
        default:
            return type;
    }
};

const getGitHubEvent = (e?: AuditLogEntry) => {
    if (!e) {
        return;
    }

    return (e.details as any)?.githubEvent;
};

const getGitHubPayload = (e?: AuditLogEntry) => {
    if (!e) {
        return;
    }

    return (e.details as any)?.payload;
};

export default ({ entry }: Props) => {
    const triggeredBy = entry.triggeredBy;
    if (!triggeredBy || !triggeredBy.trigger) {
        return <> - </>;
    }

    const type = triggeredBy.trigger.eventSource;

    const icon = getIcon(type);
    const title = getTitle(type);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [auditEntry, setAuditEntry] = useState<AuditLogEntry | undefined>();

    const loadGitHubEvent = useCallback(
        async (externalEventId?: string) => {
            if (!externalEventId) {
                return;
            }

            // ±1 hour range
            const createdAt = parseISO(entry.createdAt);
            const after = format(
                sub(createdAt, {
                    hours: 1
                }),
                DATE_TIME_FORMAT
            );
            const before = format(
                add(createdAt, {
                    hours: 1
                }),
                DATE_TIME_FORMAT
            );

            setLoading(true);
            setError(undefined);
            setAuditEntry(undefined);
            try {
                const result = await apiList({
                    object: AuditObject.EXTERNAL_EVENT,
                    after,
                    before,
                    details: { eventId: externalEventId },
                    offset: 0,
                    limit: 1
                });

                if (result.items && result.items.length > 0) {
                    setAuditEntry(result.items[0]);
                }
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        },
        [entry]
    );

    return (
        <Modal dimmer="inverted" trigger={<Button icon={icon} basic={true} content={title} />}>
            <Header icon={icon} content={`${title} Trigger`} />
            <Modal.Content scrolling={true}>
                {error && <RequestErrorActivity error={error} />}

                {type === 'github' && (
                    <>
                        <Table definition={true}>
                            <Table.Body>
                                <Table.Row>
                                    <Table.Cell collapsing={true}>GitHub Delivery ID</Table.Cell>
                                    <Table.Cell>
                                        {triggeredBy.externalEventId ? (
                                            <WithCopyToClipboard
                                                value={triggeredBy.externalEventId}>
                                                {triggeredBy.externalEventId}
                                            </WithCopyToClipboard>
                                        ) : (
                                            ''
                                        )}
                                    </Table.Cell>
                                </Table.Row>
                                <Table.Row>
                                    <Table.Cell collapsing={true}>Event</Table.Cell>
                                    <Table.Cell>
                                        <Popup
                                            trigger={
                                                <Button
                                                    loading={loading}
                                                    onClick={() =>
                                                        loadGitHubEvent(triggeredBy.externalEventId)
                                                    }>
                                                    Load Data
                                                </Button>
                                            }
                                            content="Click to load the GitHub event's data."
                                        />
                                    </Table.Cell>
                                </Table.Row>
                            </Table.Body>
                        </Table>
                    </>
                )}

                {getGitHubPayload(auditEntry) && (
                    <>
                        <Divider horizontal={true}>GitHub Notification</Divider>
                        <Table definition={true}>
                            <Table.Row>
                                <Table.Cell collapsing={true}>Event Type</Table.Cell>
                                <Table.Cell>{getGitHubEvent(auditEntry)}</Table.Cell>
                            </Table.Row>
                        </Table>
                        <ReactJson
                            src={getGitHubPayload(auditEntry)}
                            name={null}
                            enableClipboard={true}
                            displayDataTypes={false}
                        />
                    </>
                )}

                <Divider horizontal={true}>Process Details</Divider>

                <ReactJson
                    src={triggeredBy}
                    collapsed={false}
                    name={null}
                    enableClipboard={true}
                    displayDataTypes={false}
                />
            </Modal.Content>
        </Modal>
    );
};
