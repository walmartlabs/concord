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
import { Button, Input, Menu, Loader } from 'semantic-ui-react';

import { RequestError, ConcordId } from '../../../api/common';
import { SearchFilter } from '../../../api/org/process';
import { ProcessEntry, ProcessStatus } from '../../../api/process';
import {
    ProcessList,
    ProcessStatusDropdown,
    RequestErrorMessage,
    BulkProcessActionDropdown
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
    refresh: () => void;
}

interface State {
    filterState: SearchFilter;
    selectedProcessIds: ConcordId[];
}

const toState = (selectedProcessIds: ConcordId[], data?: SearchFilter): State => {
    return { filterState: data || {}, selectedProcessIds };
};

const compareFilter = (left?: SearchFilter, right?: SearchFilter): boolean => {
    if (left === right) {
        return true;
    }
    if (!left || !right) {
        return false;
    }
    if (left.status !== right.status || left.initiator !== right.initiator) {
        return false;
    }
    return true;
};

class ProcessListWithSearch extends React.PureComponent<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = toState([], this.props.filterProps);
        this.onSelectProcess = this.onSelectProcess.bind(this);
    }

    componentDidUpdate(prevProps: Props) {
        const { filterProps } = this.props;
        const { filterState } = this.state;
        if (compareFilter(filterProps, filterState)) {
            this.setState({ filterState: filterProps || {} });
        }
    }

    handleStatusChange(s?: string) {
        const { filterState } = this.state;
        const status: ProcessStatus = s && s.length > 0 ? ProcessStatus[s] : undefined;

        this.setState({ filterState: { initiator: filterState.initiator, status } });
    }

    handleInitiatorChange(s?: string) {
        const { filterState } = this.state;
        const initiator = s && s.length > 0 ? s : undefined;

        this.setState({ filterState: { status: filterState.status, initiator } });
    }

    onSelectProcess(processIds: ConcordId[]) {
        this.setState({ selectedProcessIds: processIds });
    }

    render() {
        const {
            processes,
            showInitiatorFilter = false,
            columns,
            projectName,
            loadError,
            loading,
            refresh
        } = this.props;

        const { filterState } = this.state;

        if (loadError) {
            return <RequestErrorMessage error={loadError} />;
        }

        if (loading) {
            return <Loader active={true} />;
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
                        <Button basic={true} icon="refresh" loading={loading} onClick={refresh} />
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
                                onChange={(ev, data) => this.handleInitiatorChange(data.value)}
                            />
                        </Menu.Item>
                    )}

                    <Menu.Item position="right">
                        <BulkProcessActionDropdown
                            data={this.state.selectedProcessIds}
                            refresh={refresh}
                        />
                    </Menu.Item>
                </Menu>

                <ProcessList
                    data={processes}
                    filterProps={filterState}
                    columns={cols}
                    refresh={refresh}
                    onSelectProcess={this.onSelectProcess}
                />
            </>
        );
    }
}

export default ProcessListWithSearch;
