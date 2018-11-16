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
import { Button, Input, Menu } from 'semantic-ui-react';

import { RequestError, ConcordId } from '../../../api/common';
import { SearchFilter } from '../../../api/org/process';
import { ProcessEntry, ProcessStatus } from '../../../api/process';
import {
    ProcessList,
    ProcessStatusDropdown,
    RequestErrorMessage,
    BulkProcessActionDropdown,
    Pagination
} from '../../molecules';
import { ColumnDefinition } from '../../../api/org';

import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    UPDATED_AT_COLUMN
} from '../../molecules/ProcessList';

const defaultColumns = [
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN
];
const withoutProjectColumns = [
    INSTANCE_ID_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN
];

interface Props {
    processes: ProcessEntry[];

    orgName?: string;
    projectName?: string;

    filterProps?: SearchFilter;
    showInitiatorFilter?: boolean;

    columns: ColumnDefinition[];

    loading: boolean;
    loadError: RequestError;

    next?: number;
    prev?: number;

    usePagination?: boolean;

    refresh: (filters?: SearchFilter) => void;
}

interface State {
    filterState: SearchFilter;
    selectedProcessIds: ConcordId[];
}

const toState = (selectedProcessIds: ConcordId[], data?: SearchFilter): State => {
    return { filterState: data || {}, selectedProcessIds };
};

class ProcessListWithSearch extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = toState([], this.props.filterProps);
        this.onSelectProcess = this.onSelectProcess.bind(this);
        this.onRefresh = this.onRefresh.bind(this);
        this.handlePrev = this.handlePrev.bind(this);
        this.handleNext = this.handleNext.bind(this);
    }

    handleStatusChange(s?: string) {
        const { filterState } = this.state;
        const { refresh } = this.props;
        const status: ProcessStatus = s && s.length > 0 ? ProcessStatus[s] : undefined;
        if (filterState.status !== status) {
            this.setState({
                filterState: { initiator: filterState.initiator, status, limit: filterState.limit }
            });
            refresh({ initiator: filterState.initiator, status, limit: filterState.limit });
        }
    }

    handleInitiatorChange(s?: string) {
        const { filterState } = this.state;
        const initiator = s && s.length > 0 ? s : undefined;

        if (filterState.initiator !== s) {
            this.setState({
                filterState: { status: filterState.status, initiator, limit: filterState.limit }
            });
        }
    }

    handleLimitChange(limit: any) {
        const { filterState } = this.state;
        const { refresh } = this.props;
        if (filterState.limit !== limit) {
            this.setState({
                filterState: { limit, initiator: filterState.initiator, status: filterState.status }
            });
            refresh({ initiator: filterState.initiator, status: filterState.status, limit });
        }
    }

    handleNext() {
        this.handleNavigation(this.props.next);
    }

    handlePrev() {
        this.handleNavigation(this.props.prev);
    }

    handleNavigation(offset?: number) {
        const { filterState } = this.state;
        const { refresh } = this.props;

        refresh({
            offset,
            status: filterState.status,
            limit: filterState.limit,
            initiator: filterState.initiator
        });
    }

    handleInitiatorOnBlur() {
        const { refresh, filterProps } = this.props;
        const { filterState } = this.state;
        const prevInitiator = filterProps ? filterProps.initiator : '';
        if (prevInitiator !== filterState.initiator) {
            refresh({
                initiator: filterState.initiator,
                status: filterState.status,
                limit: filterState.limit
            });
        }
    }

    onSelectProcess(processIds: ConcordId[]) {
        this.setState({ selectedProcessIds: processIds });
    }

    onRefresh() {
        const { refresh } = this.props;
        const { filterState } = this.state;
        refresh({
            limit: filterState.limit,
            status: filterState.status,
            initiator: filterState.initiator
        });
    }

    render() {
        const {
            processes,
            showInitiatorFilter = false,
            usePagination = false,
            columns,
            projectName,
            loadError,
            loading,
            prev,
            next
        } = this.props;

        const { filterState } = this.state;

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

                <Menu attached="top" borderless={true}>
                    <Menu.Item>
                        <Button
                            basic={true}
                            icon="refresh"
                            loading={loading}
                            onClick={this.onRefresh}
                        />
                    </Menu.Item>
                    <Menu.Item>
                        <ProcessStatusDropdown
                            value={filterState.status}
                            onChange={(ev, data) => this.handleStatusChange(data.value as string)}
                        />
                    </Menu.Item>
                    {showInitiatorFilter && (
                        <Menu.Item>
                            <Input
                                placeholder="Initiator"
                                value={filterState.initiator ? filterState.initiator : ''}
                                onBlur={() => this.handleInitiatorOnBlur()}
                                onChange={(ev, data) => this.handleInitiatorChange(data.value)}
                            />
                        </Menu.Item>
                    )}

                    <Menu.Item>
                        <BulkProcessActionDropdown
                            data={this.state.selectedProcessIds}
                            refresh={this.onRefresh}
                        />
                    </Menu.Item>
                    {usePagination && (
                        <Menu.Item position="right">
                            <Pagination
                                filterProps={filterState}
                                handleLimitChange={(limit) => this.handleLimitChange(limit)}
                                handleNext={this.handleNext}
                                handlePrev={this.handlePrev}
                                disablePrevious={prev === undefined}
                                disableNext={next === undefined}
                            />
                        </Menu.Item>
                    )}
                </Menu>

                <ProcessList
                    data={processes}
                    columns={cols}
                    refresh={this.onRefresh}
                    onSelectProcess={this.onSelectProcess}
                />
            </>
        );
    }
}

export default ProcessListWithSearch;
