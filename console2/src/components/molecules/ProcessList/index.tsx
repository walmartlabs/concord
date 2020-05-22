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
import { Link } from 'react-router-dom';
import { Checkbox, Table } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { ColumnDefinition, RenderType } from '../../../api/org';
import { canBeCancelled, ProcessEntry, ProcessFilters, ProcessStatus } from '../../../api/process';
import { HumanizedDuration, LocalTimestamp, ProcessStatusIcon } from '../../molecules';
import { TableSearchFilter } from '../../atoms';

import './styles.css';
import { parseISO } from 'date-fns';

export const STATUS_COLUMN: ColumnDefinition = {
    caption: 'Status',
    source: 'status',
    render: RenderType.PROCESS_STATUS,
    textAlign: 'center',
    collapsing: true,
    searchValueType: 'string',
    searchType: 'equals',
    searchOptions: Object.keys(ProcessStatus).map((k) => ({
        value: k,
        text: k
    }))
};

export const INSTANCE_ID_COLUMN: ColumnDefinition = {
    caption: 'Instance ID',
    source: 'instanceId',
    render: RenderType.PROCESS_LINK
};

export const PROJECT_COLUMN: ColumnDefinition = {
    caption: 'Project',
    source: 'projectName',
    render: RenderType.PROJECT_LINK
};

export const REPO_COLUMN: ColumnDefinition = {
    caption: 'Repository',
    source: 'repoName',
    render: RenderType.REPO_LINK,
    searchValueType: 'string',
    searchType: 'substring'
};

export const INITIATOR_COLUMN: ColumnDefinition = {
    caption: 'Initiator',
    source: 'initiator',
    searchValueType: 'string',
    searchType: 'substring'
};

export const CREATED_AT_COLUMN: ColumnDefinition = {
    caption: 'Created',
    source: 'createdAt',
    render: RenderType.TIMESTAMP
};

export const UPDATED_AT_COLUMN: ColumnDefinition = {
    caption: 'Updated',
    source: 'lastUpdatedAt',
    render: RenderType.TIMESTAMP
};

export const DURATION_COLUMN: ColumnDefinition = {
    caption: 'Duration',
    source: 'lastRunAt',
    render: RenderType.DURATION
};

export const TAGS_COLUMN: ColumnDefinition = {
    caption: 'Tags',
    source: 'tags',
    render: RenderType.STRING_ARRAY
};

interface Entry extends ProcessEntry {
    checked: boolean;
}

interface Props {
    data?: ProcessEntry[];
    orgName?: string;
    columns: ColumnDefinition[];
    onSelectProcess?: (selectedIds: ConcordId[]) => void;

    filterProps?: ProcessFilters;
    onFilterChange?: (column: ColumnDefinition, filterValue?: string) => void;

    showHeader?: boolean;
}

interface State {
    data: Entry[];
    active: boolean;
}

const toState = (data?: ProcessEntry[]): Entry[] => {
    return data ? data.map((e) => ({ ...e, checked: false })) : [];
};

const getValue = (source: string, e: ProcessEntry) => {
    if (e === undefined) {
        return {};
    }

    if (source.startsWith('meta.')) {
        if (e.meta === undefined) {
            return 'n/a';
        }

        const src = source.substring('meta.'.length);
        return e.meta[src];
    }

    return e[source];
};

const renderColumnContent = (e: Entry, c: ColumnDefinition) => {
    const v = getValue(c.source, e);

    switch (c.render) {
        case RenderType.PROCESS_LINK: {
            const caption = v || e.instanceId;
            return <Link to={`/process/${e.instanceId}`}>{caption}</Link>;
        }
        case RenderType.TIMESTAMP: {
            return v === undefined ? '' : <LocalTimestamp value={v} />;
        }
        case RenderType.PROJECT_LINK: {
            return <Link to={`/org/${e.orgName}/project/${e.projectName}`}>{v}</Link>;
        }
        case RenderType.REPO_LINK: {
            return (
                <Link to={`/org/${e.orgName}/project/${e.projectName}/repository/${e.repoName}`}>
                    {v}
                </Link>
            );
        }
        case RenderType.PROCESS_STATUS: {
            return <ProcessStatusIcon status={e.status} />;
        }
        case RenderType.STRING_ARRAY: {
            return v === undefined ? '' : v.join(', ');
        }
        case RenderType.DURATION: {
            if (!v || !e.lastUpdatedAt) {
                return '-';
            }

            try {
                const start = parseISO(v);
                const end = parseISO(e.lastUpdatedAt);
                return (
                    <HumanizedDuration
                        value={end.getTime() - start.getTime()}
                        hint="since last RUNNING status"
                    />
                );
            } catch (e) {
                return `Invalid value: ${v}`;
            }
        }
        default: {
            return v;
        }
    }
};

const renderColumn = (idx: number, e: Entry, c: ColumnDefinition) => {
    return (
        <Table.Cell key={idx} textAlign={c.textAlign}>
            {renderColumnContent(e, c)}
        </Table.Cell>
    );
};

const renderSearchFilter = (
    c: ColumnDefinition,
    filterProps?: ProcessFilters,
    onFilterChange?: (column: ColumnDefinition, filterValue: string) => void
) => {
    if (
        c.searchValueType === undefined ||
        onFilterChange === undefined ||
        filterProps === undefined
    ) {
        return;
    }

    const filterCurrentValue = filterProps[c.source];

    return (
        <TableSearchFilter
            column={c}
            currentValue={filterCurrentValue}
            onFilterChange={(column, filterValue) => onFilterChange(column, filterValue)}
        />
    );
};

const renderColumnCaption = (
    c: ColumnDefinition,
    filterProps?: ProcessFilters,
    onFilterChange?: (column: ColumnDefinition, filterValue: string) => void
) => {
    const searchFilter = renderSearchFilter(c, filterProps, onFilterChange);
    return (
        <Table.HeaderCell
            style={{ whiteSpace: 'nowrap' }}
            key={c.caption}
            collapsing={c.collapsing}
            singleLine={c.singleLine}>
            {c.caption}
            {searchFilter}
        </Table.HeaderCell>
    );
};

class ProcessList extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);

        this.state = { data: toState(props.data), active: false };
    }

    componentDidUpdate(prevProps: Props) {
        if (prevProps.data !== this.props.data) {
            this.setState({
                data: toState(this.props.data)
            });
        }
    }

    onToggleRow(r: Entry) {
        if (!canBeCancelled(r.status)) {
            return;
        }

        const processes = [...this.state.data];

        const idx: number = processes.findIndex((p) => p.instanceId === r.instanceId);
        if (idx === -1) {
            return;
        }

        const isChecked = processes[idx].checked;

        this.onRowSelect(r, !isChecked);
    }

    onRowSelect(r: Entry, isChecked: any) {
        const processes = [...this.state.data];

        const idx: number = processes.findIndex((p) => p.instanceId === r.instanceId);
        if (idx === -1) {
            return;
        }

        processes[idx].checked = isChecked;

        this.setSelectedProcessIds(processes);
        this.setState({ data: processes });
    }

    onAllRowsSelect(rows: Entry[], isChecked: any) {
        const processes = [...this.state.data];

        processes.forEach((p) => {
            if (canBeCancelled(p.status)) {
                const idx: number = processes.findIndex((p2) => p2.instanceId === p.instanceId);
                processes[idx].checked = isChecked;
            }
        });

        this.setSelectedProcessIds(processes);
        this.setState({ data: processes });
    }

    setSelectedProcessIds(processes: Entry[]) {
        const { onSelectProcess } = this.props;
        const selectedProcessIds: ConcordId[] = [];
        processes.forEach((p) => {
            if (p.checked) {
                selectedProcessIds.push(p.instanceId);
            }
        });
        if (onSelectProcess != null) {
            onSelectProcess(selectedProcessIds);
        }
    }

    renderTableHeader(rows: Entry[], columns: ColumnDefinition[]) {
        const { onSelectProcess, onFilterChange, filterProps } = this.props;
        const selectedProcessIds: ConcordId[] = [];
        rows.forEach((p) => {
            if (p.checked) {
                selectedProcessIds.push(p.instanceId);
            }
        });

        const cancellableProcessIds: ConcordId[] = [];
        rows.forEach((p) => {
            if (canBeCancelled(p.status)) {
                cancellableProcessIds.push(p.instanceId);
            }
        });

        const isTopCheckboxDisabled = cancellableProcessIds.length === 0;
        const isTopCheckboxSelected =
            cancellableProcessIds.length !== 0 &&
            cancellableProcessIds.length === selectedProcessIds.length;

        return (
            <Table.Row>
                {onSelectProcess !== undefined && (
                    <Table.HeaderCell collapsing={true}>
                        <Checkbox
                            onClick={(e, data) => this.onAllRowsSelect(rows, data.checked)}
                            checked={isTopCheckboxSelected}
                            disabled={isTopCheckboxDisabled}
                        />
                    </Table.HeaderCell>
                )}
                {columns.map((c) => renderColumnCaption(c, filterProps, onFilterChange))}
            </Table.Row>
        );
    }

    renderTableRow(row: Entry, columns: ColumnDefinition[]) {
        const { onSelectProcess } = this.props;
        return (
            <Table.Row key={row.instanceId} onClick={() => this.onToggleRow(row)}>
                {onSelectProcess !== undefined && (
                    <Table.Cell collapsing={true}>
                        <Checkbox
                            key={row.instanceId}
                            checked={row.checked}
                            disabled={!canBeCancelled(row.status)}
                        />
                    </Table.Cell>
                )}
                {columns.map((c, id) => renderColumn(id, row, c))}
            </Table.Row>
        );
    }

    render() {
        const { columns, onFilterChange, showHeader } = this.props;
        const { data } = this.state;

        const canBeFiltered = onFilterChange !== undefined;

        if (!data || data.length === 0) {
            if (showHeader) {
                return (
                    <div className={'container'}>
                        <Table
                            celled={true}
                            attached="bottom"
                            selectable={true}
                            style={{ borderBottom: 'none' }}>
                            <Table.Header>{this.renderTableHeader(data, columns)}</Table.Header>
                            <Table.Body>
                                <Table.Row style={{ fontWeight: 'bold' }}>
                                    <Table.Cell colSpan={columns.length}>
                                        No data available
                                    </Table.Cell>
                                </Table.Row>
                            </Table.Body>
                        </Table>
                    </div>
                );
            } else {
                return (
                    <>
                        {canBeFiltered && (
                            <div className={'container'}>
                                <Table
                                    celled={true}
                                    attached="bottom"
                                    selectable={true}
                                    style={{ borderBottom: 'none' }}>
                                    <Table.Header>
                                        {this.renderTableHeader(data, columns)}
                                    </Table.Header>
                                </Table>
                            </div>
                        )}
                        <h3>No processes found.</h3>
                    </>
                );
            }
        }

        return (
            <div style={{ overflowX: 'auto' }} className={'container'}>
                <Table celled={true} attached="bottom" selectable={true}>
                    <Table.Header>{this.renderTableHeader(data, columns)}</Table.Header>

                    <Table.Body>{data.map((p) => this.renderTableRow(p, columns))}</Table.Body>
                </Table>
            </div>
        );
    }
}

export default ProcessList;
