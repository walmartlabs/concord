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

import { parse as parseQueryString } from 'query-string';
import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router';
import { replace as pushHistory } from 'react-router-redux';
import { Button, Input, Menu } from 'semantic-ui-react';

import { queryParams, RequestError } from '../../../api/common';
import { SearchFilter } from '../../../api/org/process';
import { ProcessEntry, ProcessStatus } from '../../../api/process';
import { actions, Processes, State } from '../../../state/data/processes';
import { ProcessList, ProcessStatusDropdown, RequestErrorMessage } from '../../molecules';
import { Column } from '../../molecules/ProcessList';

interface RouteProps {
    status?: string;
    initiator?: string;
    orgName?: string;
}

interface StateProps {
    processes: ProcessEntry[];
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: (orgName?: string, projectName?: string, filters?: SearchFilter) => void;
}

interface ExternalProps {
    orgName?: string;
    projectName?: string;

    // TODO remove when we migrate to the common process search endpoint
    showInitiatorFilter?: boolean;
}

type Props = StateProps & DispatchProps & ExternalProps & RouteComponentProps<RouteProps>;

const parseSearchFilter = (s: string): SearchFilter => {
    const v: any = parseQueryString(s);
    return {
        status: v.status ? ProcessStatus[v.status as string] : undefined,
        initiator: v.initiator ? v.initiator : ''
    };
};

class ProcessListActivity extends React.Component<Props> {
    private currentFilter: SearchFilter = {};

    constructor(props: Props) {
        super(props);
        this.currentFilter = parseSearchFilter(props.location.search);
    }

    componentWillMount() {
        this.update();
    }

    componentDidUpdate(prevProps: Props) {
        const {
            location: { search: newSearch }
        } = this.props;
        const {
            location: { search: oldSearch }
        } = prevProps;

        let update = false;

        if (newSearch !== oldSearch) {
            this.currentFilter = parseSearchFilter(newSearch);
            update = true;
        }

        const { orgName: newOrgName, projectName: newProjectName } = this.props;
        const { orgName: oldOrgName, projectName: oldProjectName } = prevProps;

        if (newOrgName !== oldOrgName || newProjectName !== oldProjectName) {
            update = true;
        }

        if (update) {
            this.update();
        }
    }

    update() {
        const { orgName, projectName, load } = this.props;
        load(orgName, projectName, this.currentFilter);
    }

    handleStatusChange(s?: string) {
        this.currentFilter.status = s && s.length > 0 ? ProcessStatus[s] : undefined;
        this.update();
    }

    handleInitiatorChange(s?: string) {
        this.currentFilter.initiator = s && s.length > 0 ? s : undefined;
        this.update();
    }

    render() {
        const { loading, error, processes, projectName, showInitiatorFilter = false } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }
        if (!processes) {
            return <p>No processes found.</p>;
        }

        const showProjectColumn = !projectName;

        return (
            <>
                <Menu attached="top" borderless={true}>
                    <Menu.Item>
                        <Button
                            basic={true}
                            icon="refresh"
                            loading={loading}
                            onClick={() => this.update()}
                        />
                    </Menu.Item>
                    <Menu.Item>
                        <ProcessStatusDropdown
                            value={this.currentFilter.status}
                            onChange={(ev, data) => this.handleStatusChange(data.value as string)}
                        />
                    </Menu.Item>
                    {showInitiatorFilter && (
                        <Menu.Item>
                            <Input
                                placeholder="Initiator"
                                value={
                                    this.currentFilter.initiator ? this.currentFilter.initiator : ''
                                }
                                onChange={(ev, data) => this.handleInitiatorChange(data.value)}
                            />
                        </Menu.Item>
                    )}
                </Menu>

                <ProcessList
                    processes={processes}
                    hideColumns={showProjectColumn ? [] : [Column.PROJECT]}
                />
            </>
        );
    }
}

// TODO move to selectors
const makeProcessList = (data: Processes): ProcessEntry[] => {
    return Object.keys(data)
        .map((k) => data[k])
        .sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : 0));
};

const mapStateToProps = ({ processes }: { processes: State }): StateProps => ({
    loading: processes.loading,
    error: processes.error,
    processes: makeProcessList(processes.processById)
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (orgName?, projectName?, filters?) => {
        if (filters) {
            dispatch(pushHistory({ search: queryParams(filters) }));
        }
        dispatch(actions.listProjectProcesses(orgName, projectName, filters));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(withRouter(ProcessListActivity));
