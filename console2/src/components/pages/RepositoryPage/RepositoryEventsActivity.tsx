/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import { Button, Form, Menu, Popup, Table } from 'semantic-ui-react';

import {
    AuditAction,
    AuditLogEntry,
    AuditObject,
    list as apiList,
    PaginatedAuditLogEntries
} from '../../../api/audit/index';
import { get as getRepo } from '../../../api/org/project/repository/index';
import { LocalTimestamp, PaginationToolBar } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import { LoadingDispatch } from '../../../App';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { useApi } from '../../../hooks/useApi';
import { ConcordKey } from '../../../api/common';
import ReactJson from 'react-json-view';
import { DateTimeInput } from 'semantic-ui-calendar-react';
import { addHours, format as formatDate } from 'date-fns';
import {
    formatForApi,
    SRC_DATE_TIME_FORMAT,
    UI_DATE_TIME_FORMAT
} from '../../organisms/AuditLogActivity';
import { useRef } from 'react';

export interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    forceRefresh: any;
}

interface EventDetails {
    eventId: string;
    githubEvent: string;
    payload: {};
}

interface Filter {
    eventId?: string;
    githubEvent?: string;
    after: string;
    before: string;
}

const eventTypeOptions = [
    { value: 'commit_comment', text: 'commit_comment' },
    { value: 'create', text: 'create' },
    { value: 'delete', text: 'delete' },
    { value: 'fork', text: 'fork' },
    { value: 'issue_comment', text: 'issue_comment' },
    { value: 'issues', text: 'issues' },
    { value: 'label', text: 'label' },
    { value: 'member', text: 'member' },
    { value: 'pull_request', text: 'pull_request' },
    { value: 'pull_request_review', text: 'pull_request_review' },
    { value: 'push', text: 'push' },
    { value: 'release', text: 'release' },
    { value: 'team_add', text: 'team_add' },
    { value: 'repository', text: 'repository' }
];

export default ({ orgName, projectName, repoName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const defaultAfter = formatDate(addHours(new Date(), -8), SRC_DATE_TIME_FORMAT);
    const defaultBefore = formatDate(addHours(new Date(), 1), SRC_DATE_TIME_FORMAT);

    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst,
        resetOffset
    } = usePagination();

    const repoFullNameRef = useRef<string | undefined>('');
    const [isCalendarOpen, setCalendarOpen] = useState(false);
    const [eventTypeFilter, setEventTypeFilter] = useState<string>();
    const [eventIdFilter, setEventIdFilter] = useState<string>();
    const [eventIdFilterError, setEventIdFilterError] = useState<boolean>();
    const [before, setBefore] = useState<string>(defaultBefore);
    const [after, setAfter] = useState<string>(defaultAfter);
    const [filter, setFilter] = useState<Filter>({ after: defaultAfter, before: defaultBefore });
    const [searchEnabled, setSearchEnabled] = useState<boolean>(false);

    const fetchData = useCallback(async () => {
        if (repoFullNameRef.current === '') {
            const repo = await getRepo(orgName, projectName, repoName);
            repoFullNameRef.current = getFullRepoName(repo.url);
        } else if (repoFullNameRef.current === undefined) {
            return { next: false, items: [] };
        }

        return apiList({
            object: AuditObject.EXTERNAL_EVENT,
            action: AuditAction.ACCESS,
            details: {
                source: 'github',
                eventId: filter.eventId,
                githubEvent: filter.githubEvent,
                fullRepoName: repoFullNameRef.current
            },
            offset: paginationFilter.offset,
            limit: paginationFilter.limit,
            before: formatForApi(filter.before),
            after: formatForApi(filter.after)
        });
    }, [orgName, projectName, repoName, paginationFilter, filter]);

    const { data, error } = useApi<PaginatedAuditLogEntries>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    useEffect(() => {
        let valid = true;

        if (notEmpty(eventIdFilter)) {
            valid = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(
                eventIdFilter!
            );
            setEventIdFilterError(!valid);
        } else {
            setEventIdFilterError(false);
        }

        setSearchEnabled(valid);
    }, [eventIdFilter]);

    const disabled = data === undefined;

    return (
        <>
            <Menu secondary={true} style={{ marginTop: 0 }}>
                <Menu.Item style={{ padding: 0 }}>
                    <Form>
                        <Form.Group inline={true} style={{ margin: 0 }}>
                            <Form.Input
                                placeholder="Event ID"
                                disabled={disabled}
                                maxLength={36}
                                style={{ width: 305 }}
                                error={eventIdFilterError}
                                onChange={(ev, data) => setEventIdFilter(data.value)}
                            />

                            <Form.Select
                                placeholder="Event Type"
                                disabled={disabled}
                                clearable={true}
                                search={true}
                                options={eventTypeOptions}
                                onChange={(ev, data) => setEventTypeFilter(data.value as string)}
                            />

                            <Form.Field disabled={disabled}>
                                <Popup
                                    trigger={
                                        <Button
                                            icon="calendar alternate outline"
                                            size="large"
                                            basic={true}
                                        />
                                    }
                                    open={isCalendarOpen}
                                    onClose={() => setCalendarOpen(false)}
                                    onOpen={() => setCalendarOpen(true)}
                                    openOnTriggerMouseEnter={false}
                                    closeOnTriggerMouseLeave={false}
                                    closeOnDocumentClick={false}>
                                    <Form>
                                        <DateTimeField
                                            disabled={disabled}
                                            label="From"
                                            value={after}
                                            onChange={(data) => setAfter(data)}
                                        />

                                        <DateTimeField
                                            disabled={disabled}
                                            label="To"
                                            value={before}
                                            onChange={(data) => setBefore(data)}
                                        />
                                    </Form>
                                </Popup>
                            </Form.Field>

                            <Form.Button
                                content="Search"
                                icon="search"
                                primary={true}
                                onClick={() => {
                                    setFilter({
                                        after: after,
                                        before: before,
                                        githubEvent: eventTypeFilter,
                                        eventId: eventIdFilter
                                    });
                                    resetOffset(0);
                                    setCalendarOpen(false);
                                }}
                                disabled={disabled || !searchEnabled}
                            />
                        </Form.Group>
                    </Form>
                </Menu.Item>

                <Menu.Item style={{ padding: 0 }} position="right">
                    <PaginationToolBar
                        filterProps={paginationFilter}
                        handleLimitChange={(limit) => handleLimitChange(limit)}
                        handleNext={handleNext}
                        handlePrev={handlePrev}
                        handleFirst={handleFirst}
                        disablePrevious={paginationFilter.offset === 0}
                        disableNext={!data?.next}
                        disableFirst={paginationFilter.offset === 0}
                        disabled={disabled}
                    />
                </Menu.Item>
            </Menu>

            {error && <RequestErrorActivity error={error} />}

            <div style={{ overflowX: 'auto' }}>
                <Table
                    celled={true}
                    selectable={!disabled && data !== undefined && data.items.length > 0}
                    style={disabled ? { opacity: 0.4 } : {}}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell collapsing={true} width={2}>
                                ID
                            </Table.HeaderCell>
                            <Table.HeaderCell collapsing={true} width={1}>
                                Date
                            </Table.HeaderCell>
                            <Table.HeaderCell collapsing={true} width={2}>
                                Type
                            </Table.HeaderCell>
                            <Table.HeaderCell width={11}>Payload</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>{renderItems(data)}</Table.Body>
                </Table>
            </div>
        </>
    );
};

const renderItems = (data?: PaginatedAuditLogEntries) => {
    if (!data) {
        return (
            <Table.Row>
                <Table.Cell colSpan={4}>&nbsp;</Table.Cell>
            </Table.Row>
        );
    }

    if (data.items.length === 0) {
        return (
            <Table.Row style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={4}>No data available</Table.Cell>
            </Table.Row>
        );
    }

    return data.items.map((h, idx) => renderTableRow(idx, h));
};

const renderTableRow = (idx: number, h: AuditLogEntry) => {
    const d = h.details as EventDetails;
    return (
        <Table.Row key={idx} verticalAlign="top">
            <Table.Cell collapsing={true}>{d.eventId}</Table.Cell>
            <Table.Cell collapsing={true}>
                <LocalTimestamp value={h.entryDate} />
            </Table.Cell>
            <Table.Cell collapsing={true}>{d.githubEvent}</Table.Cell>
            <Table.Cell collapsing={true}>
                <ReactJson
                    src={d.payload}
                    name={null}
                    collapsed={true}
                    displayObjectSize={false}
                    displayDataTypes={false}
                    enableClipboard={false}
                />
            </Table.Cell>
        </Table.Row>
    );
};

const notEmpty = (x?: string) => {
    return x !== undefined && x !== '';
};

const DateTimeField = (props: {
    disabled: boolean;
    value: string;
    label: string;
    onChange: (data: string) => void;
}) => (
    <Form.Field>
        <DateTimeInput
            value={props.value}
            label={props.label}
            dateTimeFormat={UI_DATE_TIME_FORMAT}
            closable={true}
            animation={'none' as any}
            duration={0}
            disabled={props.disabled}
            onChange={(ev: {}, data: any) => {
                props.onChange(data.value as string);
            }}
        />
    </Form.Field>
);

const getFullRepoName = (repoUrl: string) => {
    let repoPath = removeSchema(repoUrl);
    repoPath = removeHost(repoPath);

    const u = repoPath.split('/');
    if (u.length < 2) {
        return undefined;
    }

    return owner(u[0]) + '/' + name(u[1]);
};

const removeSchema = (repoUrl: string) => {
    let index = repoUrl.indexOf('://');
    if (index > 0) {
        return repoUrl.substring(index + '://'.length);
    }
    index = repoUrl.indexOf('@');
    if (index > 0) {
        return repoUrl.substring(index + '@'.length);
    }
    return repoUrl;
};

const removeHost = (repoUrl: string) => {
    let index = repoUrl.indexOf(':');
    if (index > 0) {
        const portEndIndex = repoUrl.indexOf('/', index);
        if (portEndIndex > 0) {
            const port = repoUrl.substring(index + 1, portEndIndex);
            if (isPort(port)) {
                return repoUrl.substring(portEndIndex + '/'.length);
            }
        }
        return repoUrl.substring(index + ':'.length);
    }
    index = repoUrl.indexOf('/');
    if (index > 0) {
        return repoUrl.substring(index + '/'.length);
    }
    return repoUrl;
};

const isPort = (str: string) => {
    const port = Number(str);
    if (isNaN(port)) {
        return false;
    }
    return port > 0 && port <= 65535;
};

const name = (str: string) => {
    return str.replace(/^\W+|\.git$/, '');
};

const owner = (str: string) => {
    const idx = str.indexOf(':');
    if (idx > 0) {
        return str.substring(idx + 1);
    }
    return str;
};
