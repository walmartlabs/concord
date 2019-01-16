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
import { Button, Icon, Label, Popup, Table } from 'semantic-ui-react';

import { RequestError, ConcordId } from '../../../api/common';
import { ProcessEntry } from '../../../api/process';
import {
    ProcessList,
    RequestErrorMessage,
    BulkProcessActionDropdown,
    Pagination
} from '../../molecules';
import { ColumnDefinition } from '../../../api/org';

import {
    STATUS_COLUMN,
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    UPDATED_AT_COLUMN
} from '../ProcessList';
import { PaginationFilter } from '../Pagination';
import { ProcessFilters } from '../../../api/org/process';

const defaultColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN
];
const withoutProjectColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN
];

interface Props {
    processes: ProcessEntry[];

    orgName?: string;
    projectName?: string;

    processFilters?: ProcessFilters;
    paginationFilter?: PaginationFilter;

    showInitiatorFilter?: boolean;

    columns: ColumnDefinition[];

    loading: boolean;
    loadError: RequestError;

    next?: number;
    prev?: number;

    usePagination?: boolean;

    refresh: (processFilters?: ProcessFilters, paginationFilters?: PaginationFilter) => void;
}

interface State {
    processFilters: ProcessFilters;
    paginationFilter: PaginationFilter;
    selectedProcessIds: ConcordId[];
}

const toState = (
    selectedProcessIds: ConcordId[],
    processFilters?: ProcessFilters,
    paginationFilter?: PaginationFilter
): State => {
    return {
        paginationFilter: paginationFilter || {},
        processFilters: processFilters || {},
        selectedProcessIds
    };
};

function hasFilter(processFilters: ProcessFilters, columns: ColumnDefinition[]) {
    return (
        Object.keys(processFilters)
            .filter((k) => processFilters[k] !== '')
            .filter((k) => columns.find((c) => c.source === k) !== undefined).length > 0
    );
}

function renderFilter(
    filterValue: string,
    c: ColumnDefinition,
    clearFilter: (source: string) => void
) {
    return (
        <Label key={c.source} as="a" onClick={() => clearFilter(c.source)}>
            {c.caption}:<Label.Detail>{filterValue}</Label.Detail>
            <Icon name="delete" />
        </Label>
    );
}

function getDefinition(source: string, cols: ColumnDefinition[]) {
    for (const c of cols) {
        if (c.source === source) {
            return c;
        }
    }
    return { source, caption: 'n/a' };
}

function renderFiltersToolbar(
    cols: ColumnDefinition[],
    processFilters: ProcessFilters,
    clearFilter: (source: string) => void
) {
    return Object.keys(processFilters)
        .filter((k) => cols.find((c) => c.source === k) !== undefined)
        .map((k) => renderFilter(processFilters[k], getDefinition(k, cols), clearFilter));
}

class ProcessListWithSearch extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = toState([], this.props.processFilters, this.props.paginationFilter);
        this.onSelectProcess = this.onSelectProcess.bind(this);
        this.onRefresh = this.onRefresh.bind(this);
        this.handlePrev = this.handlePrev.bind(this);
        this.handleNext = this.handleNext.bind(this);
        this.handleFirst = this.handleFirst.bind(this);
        this.onFilterChange = this.onFilterChange.bind(this);
        this.onFiltersClear = this.onFiltersClear.bind(this);
        this.onFilterClear = this.onFilterClear.bind(this);
    }

    onFilterChange(column: ColumnDefinition, filterValue?: string) {
        const { processFilters, paginationFilter } = this.state;
        const { refresh } = this.props;

        const newProcessFilters = {};
        Object.keys(processFilters)
            .filter((k) => k !== column.source)
            .forEach((k) => (newProcessFilters[k] = processFilters[k]));

        if (filterValue !== undefined) {
            newProcessFilters[column.source] = filterValue;
        }

        this.setState({ processFilters: newProcessFilters });
        refresh(newProcessFilters, paginationFilter);
    }

    onFiltersClear() {
        const { paginationFilter } = this.state;
        const { refresh } = this.props;
        const processFilters = {};
        this.setState({ processFilters });
        refresh(processFilters, paginationFilter);
    }

    onFilterClear(source: string) {
        const { paginationFilter, processFilters } = this.state;
        const { refresh } = this.props;
        const newProcessFilters = {};
        Object.keys(processFilters)
            .filter((k) => k !== source)
            .forEach((k) => (newProcessFilters[k] = processFilters[k]));
        this.setState({ processFilters: newProcessFilters });
        refresh(newProcessFilters, paginationFilter);
    }

    handleLimitChange(limit: any) {
        const { paginationFilter, processFilters } = this.state;
        const { refresh } = this.props;
        if (paginationFilter.limit !== limit) {
            this.setState({
                paginationFilter: { limit }
            });
            refresh(processFilters, { limit });
        }
    }

    handleNext() {
        this.handleNavigation(this.props.next);
    }

    handlePrev() {
        this.handleNavigation(this.props.prev);
    }

    handleFirst() {
        this.handleNavigation(0);
    }

    handleNavigation(offset?: number) {
        const { paginationFilter, processFilters } = this.state;
        const { refresh } = this.props;

        refresh(processFilters, { offset, limit: paginationFilter.limit });
    }

    onSelectProcess(processIds: ConcordId[]) {
        this.setState({ selectedProcessIds: processIds });
    }

    onRefresh() {
        const { refresh } = this.props;
        const { processFilters, paginationFilter } = this.state;
        refresh(processFilters, paginationFilter);
    }

    renderFilterLabels(cols: ColumnDefinition[], processFilters: ProcessFilters, loading: boolean) {
        return (
            <>
                <span style={{ marginRight: '10px' }}>Active filters:</span>
                {renderFiltersToolbar(cols, processFilters, this.onFilterClear)}
                <Popup
                    trigger={
                        <Button
                            basic={true}
                            icon="ban"
                            loading={loading}
                            style={{ marginLeft: '10px' }}
                            onClick={this.onFiltersClear}
                        />
                    }
                    content="Clear filters"
                />
            </>
        );
    }

    render() {
        const {
            processes,
            usePagination = false,
            columns,
            projectName,
            loadError,
            loading,
            prev,
            next
        } = this.props;

        const { processFilters, paginationFilter } = this.state;

        if (loadError) {
            return <RequestErrorMessage error={loadError} />;
        }

        if (!processes) {
            return <p>No processes found.</p>;
        }

        const showProjectColumn = !projectName;
        const cols = columns || (showProjectColumn ? defaultColumns : withoutProjectColumns);

        return (
            <>
                {loadError && <RequestErrorMessage error={loadError} />}

                <Table attached="top" basic={true} style={{ borderBottom: 0 }}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell collapsing={true}>
                                <Button
                                    basic={true}
                                    icon="refresh"
                                    loading={loading}
                                    onClick={this.onRefresh}
                                />
                            </Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>
                                <BulkProcessActionDropdown
                                    data={this.state.selectedProcessIds}
                                    refresh={this.onRefresh}
                                />
                            </Table.HeaderCell>
                            <Table.HeaderCell>
                                {hasFilter(processFilters, columns) &&
                                    this.renderFilterLabels(cols, processFilters, loading)}
                            </Table.HeaderCell>
                            <Table.HeaderCell collapsing={true} style={{ fontWeight: 'normal' }}>
                                {usePagination && (
                                    <Pagination
                                        filterProps={paginationFilter}
                                        handleLimitChange={(limit) => this.handleLimitChange(limit)}
                                        handleNext={this.handleNext}
                                        handlePrev={this.handlePrev}
                                        handleFirst={this.handleFirst}
                                        disablePrevious={prev === undefined}
                                        disableNext={next === undefined}
                                        disableFirst={prev === undefined}
                                    />
                                )}
                            </Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>
                </Table>

                <ProcessList
                    data={processes}
                    columns={cols}
                    onSelectProcess={this.onSelectProcess}
                    filterProps={processFilters}
                    onFilterChange={this.onFilterChange}
                />
            </>
        );
    }
}

export default ProcessListWithSearch;
