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
import { Button, DropdownItemProps, Form, Menu, Popup, Table } from 'semantic-ui-react';
import ReactJson from 'react-json-view';
import { DateTimeInput } from 'semantic-ui-calendar-react';
import { addHours, format as formatDate, parse as parseDate } from 'date-fns';

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
import { FindUserField2, RequestErrorActivity } from '../../organisms';
import { RefreshButton } from '../../atoms';

// date-fns format used to parse date-time strings set by the UI component
export const SRC_DATE_TIME_FORMAT = 'yyyy-MM-dd HH:mm';
// date-fns format used to convert UI date-time strings into the API's date-time format
export const DST_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
// date-time format used by the UI component (pickers)
export const UI_DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm';

export const formatForApi = (s: string) =>
    formatDate(parseDate(s, SRC_DATE_TIME_FORMAT, new Date()), DST_DATE_TIME_FORMAT);

// converts timestamps from the UI format to the format accepted by the API
const prepareCall = (f: AuditLogFilter) => {
    const result = { ...f };

    if (result.before) {
        result.before = formatForApi(result.before);
    }

    if (result.after) {
        result.after = formatForApi(result.after);
    }

    return result;
};

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

// a toggle to force search even if the filter is unchanged
interface ForceSearch {
    force: boolean;
}

const DateTimeField = (props: {
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
            animation={'none' as any} // workaround for jittery animations in Chrome
            duration={0}
            onChange={(ev: {}, data: any) => props.onChange(data.value as string)}
        />
    </Form.Field>
);

// wrapped in "memo" with a custom prop equality function to avoid the infinite re-rendering loop
// when it tries to compare old props and new. Because React typically does only a shallow compare,
// this wrapper is necessary when the parent component triggers a re-render and a child component
// re-creates the props object
export default memo(({ filter: initialFilter, forceRefresh, showRefreshButton = true }: Props) => {
    const dispatch = React.useContext(LoadingDispatch);

    const defaultAfter = formatDate(addHours(new Date(), -8), SRC_DATE_TIME_FORMAT);
    const defaultBefore = formatDate(addHours(new Date(), 1), SRC_DATE_TIME_FORMAT);

    // contains parameters shown in the filtering controls
    const [filter, setFilter] = useState<AuditLogFilter>({
        after: defaultAfter,
        before: defaultBefore,
        ...initialFilter
    });

    // contains parameters used for search
    // whenever the Search button is clicked the filters are copied here in order
    // to trigger the API call
    const [effectiveFilter, setEffectiveFilter] = useState<AuditLogFilter & ForceSearch>({
        ...filter,
        force: false
    });

    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst
    } = usePagination();

    const fetchData = useCallback(() => {
        return apiList({ ...prepareCall(effectiveFilter), ...paginationFilter });
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
                        <Form.Group inline={true} style={{ margin: 0 }}>
                            {showRefreshButton && (
                                <RefreshButton loading={isLoading} clickAction={() => fetch()} />
                            )}

                            <Form.Field>
                                <FindUserField2
                                    placeholder="User"
                                    onSelect={(value) => {
                                        setFilter((prev) => ({ ...prev, userId: value.id }));
                                    }}
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

                            <Form.Field>
                                <Popup
                                    trigger={
                                        <Button
                                            icon="calendar alternate outline"
                                            size="large"
                                            basic={true}
                                        />
                                    }
                                    openOnTriggerClick={true}
                                    openOnTriggerMouseEnter={false}
                                    closeOnTriggerMouseLeave={false}
                                    closeOnDocumentClick={false}>
                                    <Form>
                                        <DateTimeField
                                            label="From"
                                            value={filter.after || defaultAfter}
                                            onChange={(data) =>
                                                setFilter({ ...filter, after: data })
                                            }
                                        />

                                        <DateTimeField
                                            label="To"
                                            value={filter.before || defaultBefore}
                                            onChange={(data) =>
                                                setFilter({ ...filter, before: data })
                                            }
                                        />
                                    </Form>
                                </Popup>
                            </Form.Field>

                            <Form.Button
                                content="Search"
                                icon="search"
                                primary={true}
                                onClick={() =>
                                    setEffectiveFilter((prev) => ({
                                        ...filter,
                                        force: !prev.force
                                    }))
                                }
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
                        disableNext={data === undefined ? true : !data.next}
                        disableFirst={paginationFilter.offset === 0}
                        disabled={data === undefined}
                    />
                </Menu.Item>
            </Menu>

            {error && <RequestErrorActivity error={error} />}
            {data && data.items.length === 0 && <h3>No audit log entries found.</h3>}

            {data && data.items.length > 0 && (
                <div style={{ overflowX: 'auto' }}>
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
                                            <ReactJson
                                                src={e.details}
                                                name={null}
                                                collapsed={true}
                                            />
                                        </Table.Cell>
                                    </Table.Row>
                                ))}
                        </Table.Body>
                    </Table>
                </div>
            )}
        </>
    );
}, areEqual);
