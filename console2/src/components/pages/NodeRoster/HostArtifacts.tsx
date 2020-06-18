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
import { useCallback, useState } from 'react';
import { Input, List, Menu, Popup, Table } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { PaginationToolBar } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import {
    HostArtifact,
    listHostArtifacts as apiList,
    PaginatedHostArtifacts
} from '../../../api/noderoster';
import { useApi } from '../../../hooks/useApi';
import { InputOnChangeData } from 'semantic-ui-react/dist/commonjs/elements/Input/Input';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { LoadingDispatch } from '../../../App';
import { Link } from 'react-router-dom';

export interface ExternalProps {
    hostId: ConcordId;
    forceRefresh: any;
}

const HostArtifacts = ({ hostId, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const [filter, setFilter] = useState<string>();
    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst
    } = usePagination();

    const fetchData = useCallback(() => {
        return apiList(hostId, paginationFilter.offset, paginationFilter.limit, filter);
    }, [hostId, paginationFilter, filter]);

    const { data, error } = useApi<PaginatedHostArtifacts>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    const filterChangeHandler = useCallback(
        (event: React.ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => {
            setFilter(data.value);
        },
        []
    );

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <Menu secondary={true}>
                <Menu.Item style={{ paddingTop: 0, paddingBottom: 0 }}>
                    <Input
                        icon="search"
                        placeholder="Filter..."
                        onChange={filterChangeHandler}
                        disabled={data === undefined}
                    />
                </Menu.Item>

                <Menu.Item style={{ padding: 0 }} position={'right'}>
                    <PaginationToolBar
                        limit={paginationFilter.limit}
                        handleLimitChange={(limit) => handleLimitChange(limit)}
                        handleNext={handleNext}
                        handlePrev={handlePrev}
                        handleFirst={handleFirst}
                        disablePrevious={paginationFilter.offset === 0}
                        disableNext={data === undefined ? true : !data.next}
                        disableFirst={paginationFilter.offset === 0}
                        disabled={data === undefined}
                    />
                </Menu.Item>
            </Menu>

            <HostArtifactList data={data?.items} />
        </>
    );
};

interface HostArtifactListProps {
    data?: HostArtifact[];
}

const HostArtifactList = ({ data }: HostArtifactListProps) => {
    return (
        <Table
            celled={true}
            attached="bottom"
            selectable={true}
            style={data === undefined ? { opacity: 0.4 } : {}}>
            <Table.Header>
                <Table.Row>
                    <Table.HeaderCell collapsing={true}>Filename</Table.HeaderCell>
                    <Table.HeaderCell collapsing={true}>Process ID</Table.HeaderCell>
                </Table.Row>
            </Table.Header>

            {data && <Table.Body>{renderTableBody(data)}</Table.Body>}
        </Table>
    );
};

const renderTableRow = (h: HostArtifact, idx: number) => {
    return (
        <Table.Row key={idx}>
            <Table.Cell collapsing={true}>
                <Popup
                    key={idx}
                    wide="very"
                    mouseEnterDelay={500}
                    content={h.url}
                    trigger={
                        <List.Item key={idx}>
                            <List.Content verticalAlign={'middle'}>
                                {getArtifactName(h.url)}
                            </List.Content>
                        </List.Item>
                    }
                />
            </Table.Cell>
            <Table.Cell collapsing={true}>
                <Link to={`/process/${h.processInstanceId}`}>{h.processInstanceId}</Link>
            </Table.Cell>
        </Table.Row>
    );
};

const renderTableBody = (data: HostArtifact[]) => {
    if (data.length === 0) {
        return (
            <Table.Row style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={2}>No data available</Table.Cell>
            </Table.Row>
        );
    }

    return data.map((h, idx) => renderTableRow(h, idx));
};

const getArtifactName = (url: string) => {
    return url.split(/[/]+/).pop();
};

export default HostArtifacts;
