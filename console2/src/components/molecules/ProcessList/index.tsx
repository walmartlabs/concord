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
import { ColumnDefinition, SearchType, SearchValueType } from '../../../api/org';
import { canBeCancelled, ProcessEntry, ProcessStatus } from '../../../api/process';
import { LocalTimestamp, ProcessStatusIcon } from '../../molecules';
import { TableSearchFilter } from '../../atoms';
import { ProcessFilters } from '../../../api/org/process';

export const STATUS_COLUMN: ColumnDefinition = {
    caption: 'Status',
    source: 'status',
    render: 'process-status',
    textAlign: 'center',
    collapsing: true,
    searchValueType: SearchValueType.STRING,
    searchType: SearchType.EQUALS,
    searchOptions: Object.keys(ProcessStatus).map((k) => ({
        value: k,
        text: k
    }))
};

export const INSTANCE_ID_COLUMN: ColumnDefinition = {
    caption: 'Instance ID',
    source: 'instanceId',
    render: 'process-link'
};

export const PROJECT_COLUMN: ColumnDefinition = {
    caption: 'Project',
    source: 'projectName',
    render: 'project-link'
};

export const REPO_COLUMN: ColumnDefinition = {
    caption: 'Repository',
    source: 'repoName',
    render: 'repo-link'
};

export const INITIATOR_COLUMN: ColumnDefinition = {
    caption: 'Initiator',
    source: 'initiator',
    searchValueType: SearchValueType.STRING,
    searchType: SearchType.SUBSTRING
};

export const CREATED_AT_COLUMN: ColumnDefinition = {
    caption: 'Created',
    source: 'createdAt',
    render: 'timestamp'
};

export const UPDATED_AT_COLUMN: ColumnDefinition = {
    caption: 'Updated',
    source: 'lastUpdatedAt',
    render: 'timestamp'
};

interface Entry extends ProcessEntry {
    checked: boolean;
}

interface Props {
    data: ProcessEntry[];
    orgName?: string;
    columns: ColumnDefinition[];
    onSelectProcess?: (selectedIds: ConcordId[]) => void;

    filterProps?: ProcessFilters;
    onFilterChange?: (column: ColumnDefinition, filterValue?: string) => void;
}

interface State {
    data: Entry[];
    active: boolean;
}

const toState = (data: ProcessEntry[]): Entry[] => {
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

    if (c.render === 'process-link') {
        const caption = v || e.instanceId;
        return <Link to={`/process/${e.instanceId}`}>{caption}</Link>;
    } else if (c.render === 'timestamp') {
        return <LocalTimestamp value={v} />;
    } else if (c.render === 'project-link') {
        return <Link to={`/org/${e.orgName}/project/${e.projectName}`}>{v}</Link>;
    } else if (c.render === 'repo-link') {
        return (
            <Link to={`/org/${e.orgName}/project/${e.projectName}/repository/${e.repoName}`}>
                {v}
            </Link>
        );
    } else if (c.render === 'process-status') {
        return <ProcessStatusIcon status={e.status} />;
    }

    return v;
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
            collapsing={c.collapsing}>
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
        const { columns, onFilterChange } = this.props;
        const { data } = this.state;

        const canBeFiltered = onFilterChange !== undefined;

        if (!data || data.length === 0) {
            return (
                <>
                    {canBeFiltered && (
                        <Table celled={true} attached="bottom" selectable={true}>
                            <Table.Header>{this.renderTableHeader(data, columns)}</Table.Header>
                        </Table>
                    )}
                    <h3>No processes found.</h3>
                </>
            );
        }

        return (
            <Table celled={true} attached="bottom" selectable={true}>
                <Table.Header>{this.renderTableHeader(data, columns)}</Table.Header>

                <Table.Body>{data.map((p, idx) => this.renderTableRow(p, columns))}</Table.Body>
            </Table>
        );
    }
}

export default ProcessList;
