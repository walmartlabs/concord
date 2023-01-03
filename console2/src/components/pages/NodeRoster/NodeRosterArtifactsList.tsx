/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { Link } from 'react-router-dom';
import { Form, Icon, Menu, Popup, Table } from 'semantic-ui-react';

import {
    HostEntry,
    HostFilter,
    listHosts as apiListHosts,
    PaginatedHostEntry
} from '../../../api/noderoster/index';
import { LocalTimestamp, PaginationToolBar } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import { LoadingDispatch } from '../../../App';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { useApi } from '../../../hooks/useApi';

export interface ExternalProps {
    forceRefresh: any;
}

const NodeRosterArtifactsList = ({ forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);
    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst,
        resetOffset
    } = usePagination();
    const [hostFilter, setHostFilter] = useState<string>();
    const [artifactFilter, setArtifactFilter] = useState<string>();
    const [filter, setFilter] = useState<HostFilter>();

    const fetchData = useCallback(() => {
        return apiListHosts(paginationFilter.offset, paginationFilter.limit, ['artifacts'], filter);
    }, [paginationFilter, filter]);

    const { data, error } = useApi<PaginatedHostEntry>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    const disabled = data === undefined;

    return (
        <>
            <Menu secondary={true} style={{ marginTop: 0 }}>
                <Menu.Item style={{ padding: 0 }}>
                    <Form>
                        <Form.Group widths="equal" inline={true} style={{ margin: 0 }}>
                            <Form.Input
                                placeholder="Hostname"
                                disabled={disabled}
                                onChange={(ev, data) => setHostFilter(data.value)}
                            />

                            <Form.Input
                                placeholder="Artifact"
                                disabled={disabled}
                                onChange={(ev, data) => setArtifactFilter(data.value)}
                            />

                            <Form.Button
                                content="Search"
                                icon="search"
                                primary={true}
                                onClick={() => {
                                    setFilter({
                                        host: hostFilter,
                                        artifact: artifactFilter
                                    });
                                    resetOffset(0);
                                }}
                                disabled={disabled}
                            />
                        </Form.Group>
                    </Form>
                </Menu.Item>

                <Menu.Item style={{ padding: 0 }} position="right">
                    <PaginationToolBar
                        limit={paginationFilter.limit}
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

            <Table celled={true} selectable={!disabled} style={disabled ? { opacity: 0.4 } : {}}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell width={8}>Hostname</Table.HeaderCell>
                        <Table.HeaderCell width={2}>
                            Registered At{' '}
                            <Popup
                                trigger={<Icon name="question circle outline" />}
                                content="Date and time when the host was registered by Node Roster"
                            />
                        </Table.HeaderCell>
                        <Table.HeaderCell width={6}>Filename</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>

                <Table.Body>{renderItems(data)}</Table.Body>
            </Table>
        </>
    );
};

const renderItems = (data?: PaginatedHostEntry) => {
    if (!data) {
        return (
            <tr>
                <Table.Cell colSpan={3}>&nbsp;</Table.Cell>
            </tr>
        );
    }

    if (data.items.length === 0) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={3}>No data available</Table.Cell>
            </tr>
        );
    }

    return data.items.map((h, idx) => renderTableRow(idx, h));
};

const renderTableRow = (idx: number, h: HostEntry) => {
    return (
        <Table.Row key={idx}>
            <Table.Cell collapsing={true}>
                <Link to={`/noderoster/host/${h.id}`}>{h.name}</Link>
            </Table.Cell>
            <Table.Cell collapsing={true}>
                <LocalTimestamp value={h.createdAt} />
            </Table.Cell>
            <Table.Cell collapsing={true}>{getArtifactName(h.artifactUrl)}</Table.Cell>
        </Table.Row>
    );
};

const getArtifactName = (url?: string) => {
    if (url === undefined) {
        return undefined;
    }
    return url.split(/[/]+/).pop();
};

export default NodeRosterArtifactsList;
