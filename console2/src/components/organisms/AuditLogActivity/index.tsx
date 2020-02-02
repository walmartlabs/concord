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
import { memo, useCallback, useState } from 'react';
import { Form, Menu, Table, DropdownItemProps } from 'semantic-ui-react';
import ReactJson from 'react-json-view';

import {
    AuditAction,
    AuditLogFilter,
    AuditObject,
    list as apiList,
    PaginatedAuditLogEntries
} from '../../../api/audit';
import { EntityOwnerPopup, LocalTimestamp, PaginationToolBar } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import { LoadingDispatch } from '../../../App';
import { useApi } from '../../../hooks/useApi';
import { RequestErrorActivity, FindUserField } from '../../organisms';
import { RefreshButton } from '../../atoms';

interface Props {
    filter: AuditLogFilter;
    forceRefresh?: boolean;
    showRefreshButton?: boolean;
}

const areEqual = (prev: Props, next: Props): boolean => {
    return (
        prev.forceRefresh === next.forceRefresh &&
        prev.filter.details?.orgName === next.filter.details?.orgName &&
        prev.filter.details?.jsonStoreName === next.filter.details?.jsonStoreName
    );
};

const keysToOptions = (o: any): DropdownItemProps[] =>
    Object.keys(o).map((k) => ({ key: k, text: k, value: k }));

const objectOptions = keysToOptions(AuditObject);
const actionOptions = keysToOptions(AuditAction);

// wrapped in "memo" with a custom prop equality function to avoid the infinite re-rendering loop
// when it tries to compare old props and new. Because React typically does only a shallow compare,
// this wrapper is necessary when the parent component triggers a re-render and a child component
// re-creates the props object
export default memo(({ filter: initialFilter, forceRefresh, showRefreshButton = true }: Props) => {
    const dispatch = React.useContext(LoadingDispatch);

    // contains parameters shown in the filtering controls
    const [filter, setFilter] = useState<AuditLogFilter>(initialFilter);

    // contains parameters used for search
    // whenever the Search button is clicked the filters are copied here in order
    // to trigger the API call
    const [effectiveFilter, setEffectiveFilter] = useState<AuditLogFilter>(filter);

    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst
    } = usePagination();

    const fetchData = useCallback(() => {
        return apiList({ ...effectiveFilter, ...paginationFilter });
    }, [effectiveFilter, paginationFilter]);

    const { data, error, isLoading, fetch } = useApi<PaginatedAuditLogEntries>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    return (
        <>
            <Menu secondary={true}>
                <Menu.Item style={{ padding: 0 }}>
                    <Form>
                        <Form.Group widths="equal" inline={true}>
                            {showRefreshButton && (
                                <RefreshButton loading={isLoading} clickAction={() => fetch()} />
                            )}

                            <Form.Field>
                                <FindUserField
                                    placeholder="User"
                                    onSelect={(value) =>
                                        setFilter({ ...filter, username: value.username })
                                    }
                                />
                            </Form.Field>

                            <Form.Dropdown
                                placeholder="Action"
                                clearable={true}
                                selection={true}
                                options={actionOptions}
                                onChange={(ev, data) =>
                                    setFilter({
                                        ...filter,
                                        action: data.value as AuditAction
                                    })
                                }
                            />

                            <Form.Dropdown
                                placeholder="Object"
                                clearable={true}
                                selection={true}
                                options={objectOptions}
                                onChange={(ev, data) =>
                                    setFilter({
                                        ...filter,
                                        object: data.value as AuditObject
                                    })
                                }
                            />

                            <Form.Button
                                content="Search"
                                icon="search"
                                primary={true}
                                onClick={() => setEffectiveFilter(filter)}
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
                        disableNext={data === undefined ? true : !data.next}
                        disableFirst={paginationFilter.offset === 0}
                        disabled={data === undefined}
                    />
                </Menu.Item>
            </Menu>

            {error && <RequestErrorActivity error={error} />}
            {data && data.items.length === 0 && <h3>No audit log entries found.</h3>}

            {data && data.items.length > 0 && (
                <Table>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell collapsing={true}>Date</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>Action</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>Object</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>User</Table.HeaderCell>
                            <Table.HeaderCell>Details</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {data &&
                            data.items &&
                            data.items.map((e, idx) => (
                                <Table.Row key={idx} verticalAlign="top">
                                    <Table.Cell collapsing={true}>
                                        <LocalTimestamp value={e.entryDate} />
                                    </Table.Cell>
                                    <Table.Cell collapsing={true}>{e.action}</Table.Cell>
                                    <Table.Cell collapsing={true}>{e.object}</Table.Cell>
                                    <Table.Cell collapsing={true}>
                                        {e.user ? <EntityOwnerPopup data={e.user} /> : '-'}
                                    </Table.Cell>
                                    <Table.Cell>
                                        <ReactJson src={e.details} name={null} collapsed={true} />
                                    </Table.Cell>
                                </Table.Row>
                            ))}
                    </Table.Body>
                </Table>
            )}
        </>
    );
}, areEqual);
