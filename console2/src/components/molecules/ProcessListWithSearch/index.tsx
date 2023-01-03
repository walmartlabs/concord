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
import { Button, Icon, Label, Popup, Table } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { ProcessEntry } from '../../../api/process';
import { Pagination } from '../../../state/data/processes';
import { ProcessList, BulkProcessActionDropdown, PaginationToolBar } from '../../molecules';
import { ColumnDefinition } from '../../../api/org';

import {
    STATUS_COLUMN,
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN
} from '../ProcessList';
import { ProcessFilters } from '../../../api/process';

import '../ProcessList/styles.css';

// list of columns for the default process list configuration
const defaultColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN
];

// columns used on the list of a project's processes
const withoutProjectColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN
];

interface Props {
    processes?: ProcessEntry[];

    orgName?: string;
    projectName?: string;

    processFilters?: ProcessFilters;
    paginationFilter?: Pagination;

    showInitiatorFilter?: boolean;

    columns: ColumnDefinition[];

    loading: boolean;

    next?: number;
    prev?: number;

    usePagination?: boolean;

    refresh: (processFilters?: ProcessFilters, paginationFilters?: Pagination) => void;
}

interface State {
    processFilters: ProcessFilters;
    selectedProcessIds: ConcordId[];
}

const toState = (selectedProcessIds: ConcordId[], processFilters?: ProcessFilters): State => {
    return {
        processFilters: processFilters || {},
        selectedProcessIds
    };
};

const hasFilter = (processFilters: ProcessFilters, columns: ColumnDefinition[]) => {
    return (
        Object.keys(processFilters)
            .filter((k) => processFilters[k] !== '')
            .filter((k) => columns.find((c) => c.source === k) !== undefined).length > 0
    );
};

const renderFilter = (
    filterValue: string,
    c: ColumnDefinition,
    clearFilter: (source: string) => void
) => {
    return (
        <Label key={c.source} as="a" onClick={() => clearFilter(c.source)}>
            {c.caption}:<Label.Detail>{filterValue}</Label.Detail>
            <Icon name="delete" />
        </Label>
    );
};

const getDefinition = (source: string, cols: ColumnDefinition[]) => {
    for (const c of cols) {
        if (c.source === source) {
            return c;
        }
    }
    return { source, caption: 'n/a' };
};

const getFilterText = (column: ColumnDefinition, value: string) => {
    if (column.searchOptions) {
        const option = column.searchOptions.find((o) => o.value === value);
        if (option !== undefined) {
            return option.text;
        }
    }
    return value;
};

const renderFiltersToolbar = (
    cols: ColumnDefinition[],
    processFilters: ProcessFilters,
    clearFilter: (source: string) => void
) => {
    return Object.keys(processFilters)
        .filter((k) => cols.find((c) => c.source === k) !== undefined)
        .map((k) => {
            const c = getDefinition(k, cols);
            return renderFilter(getFilterText(c, processFilters[k]), c, clearFilter);
        });
};

class ProcessListWithSearch extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = toState([], this.props.processFilters);
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
        const { processFilters } = this.state;
        const { refresh, paginationFilter } = this.props;

        const newProcessFilters = {};
        Object.keys(processFilters)
            .filter((k) => k !== column.source)
            .forEach((k) => (newProcessFilters[k] = processFilters[k]));

        if (filterValue !== undefined && filterValue !== '') {
            newProcessFilters[column.source] = filterValue;
        }

        this.setState({ processFilters: newProcessFilters });
        refresh(newProcessFilters, paginationFilter);
    }

    onFiltersClear() {
        const { refresh, paginationFilter } = this.props;
        const processFilters = {};
        this.setState({ processFilters });
        refresh(processFilters, paginationFilter);
    }

    onFilterClear(source: string) {
        const { processFilters } = this.state;
        const { refresh, paginationFilter } = this.props;
        const newProcessFilters = {};
        Object.keys(processFilters)
            .filter((k) => k !== source)
            .forEach((k) => (newProcessFilters[k] = processFilters[k]));
        this.setState({ processFilters: newProcessFilters });
        refresh(newProcessFilters, paginationFilter);
    }

    handleLimitChange(limit: any) {
        const { processFilters } = this.state;
        const { paginationFilter, refresh } = this.props;
        if (!paginationFilter || paginationFilter.limit !== limit) {
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
        const { processFilters } = this.state;
        const { refresh, paginationFilter } = this.props;

        refresh(processFilters, { offset, limit: paginationFilter && paginationFilter.limit });
    }

    onSelectProcess(processIds: ConcordId[]) {
        this.setState({ selectedProcessIds: processIds });
    }

    onRefresh() {
        const { refresh, paginationFilter } = this.props;
        const { processFilters } = this.state;
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
            paginationFilter,
            usePagination = false,
            columns,
            projectName,
            loading,
            prev,
            next
        } = this.props;

        const { processFilters } = this.state;

        const showProjectColumn = !projectName;
        const displayColumns =
            columns || (showProjectColumn ? defaultColumns : withoutProjectColumns);

        return (
            <>
                <div className={'container'}>
                    <Table attached="top" basic={true} style={{ borderBottom: 'none' }}>
                        <Table.Header>
                            <Table.Row>
                                <Table.HeaderCell
                                    collapsing={true}
                                    style={{ borderBottom: 'none' }}>
                                    <BulkProcessActionDropdown
                                        data={this.state.selectedProcessIds}
                                        refresh={this.onRefresh}
                                    />
                                </Table.HeaderCell>
                                <Table.HeaderCell style={{ borderBottom: 'none' }}>
                                    {hasFilter(processFilters, displayColumns) &&
                                        this.renderFilterLabels(
                                            displayColumns,
                                            processFilters,
                                            loading
                                        )}
                                </Table.HeaderCell>
                                <Table.HeaderCell
                                    collapsing={true}
                                    style={{ fontWeight: 'normal', borderBottom: 'none' }}>
                                    {usePagination && (
                                        <PaginationToolBar
                                            limit={paginationFilter?.limit}
                                            handleLimitChange={(limit) =>
                                                this.handleLimitChange(limit)
                                            }
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
                </div>

                <ProcessList
                    data={processes}
                    columns={displayColumns}
                    onSelectProcess={this.onSelectProcess}
                    filterProps={processFilters}
                    onFilterChange={this.onFilterChange}
                />
            </>
        );
    }
}

export default ProcessListWithSearch;
