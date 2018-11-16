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

import { queryParams, RequestError } from '../../../api/common';
import { SearchFilter } from '../../../api/org/process';
import { ProcessEntry, ProcessStatus } from '../../../api/process';
import { actions, PaginatedProcesses, State } from '../../../state/data/processes';
import { RequestErrorMessage } from '../../molecules';
import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    REPO_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    UPDATED_AT_COLUMN
} from '../../molecules/ProcessList';
import { ColumnDefinition } from '../../../api/org';
import ProcessListWithSearch from '../../molecules/ProcessListWithSearch';

const defaultColumns = [
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN
];
const withoutProjectColumns = [
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN
];

interface RouteProps {
    status?: string;
    initiator?: string;
    orgName?: string;
    projectName?: string;
}

interface StateProps {
    processes: ProcessEntry[];
    loading: boolean;
    loadError: RequestError;
    next?: number;
    prev?: number;
}

interface DispatchProps {
    load: (orgName?: string, projectName?: string, filters?: SearchFilter) => void;
}

interface ExternalProps {
    orgName?: string;
    projectName?: string;

    // TODO remove when we migrate to the common process search endpoint
    showInitiatorFilter?: boolean;
    usePagination?: boolean;

    columns?: ColumnDefinition[];
}
type Props = StateProps & DispatchProps & ExternalProps & RouteComponentProps<RouteProps>;

const parseSearchFilter = (s: string): SearchFilter => {
    const v: any = parseQueryString(s);
    return {
        status: v.status ? ProcessStatus[v.status as string] : undefined,
        initiator: v.initiator ? v.initiator : '',
        beforeCreatedAt: v.beforeCreatedAt ? v.beforeCreatedAt : undefined,
        limit: v.limit ? v.limit : 50,
        offset: v.offset ? v.offset : 0
    };
};

class ProcessListActivity extends React.Component<Props> {
    constructor(props: Props) {
        super(props);
    }

    componentWillMount() {
        const { orgName, projectName, load, location } = this.props;
        const filters = parseSearchFilter(location.search);
        load(orgName, projectName, filters);
    }

    render() {
        const {
            processes,
            showInitiatorFilter = false,
            usePagination = false,
            columns,
            orgName,
            projectName,
            loadError,
            loading,
            load,
            history,
            next,
            prev
        } = this.props;

        if (loadError) {
            return <RequestErrorMessage error={loadError} />;
        }

        if (!processes) {
            return <h3>No processes found.</h3>;
        }

        const showProjectColumn = !projectName;
        const cols = columns || (showProjectColumn ? defaultColumns : withoutProjectColumns);
        const filter = parseSearchFilter(history.location.search);
        return (
            <>
                {loadError && <RequestErrorMessage error={loadError} />}

                <ProcessListWithSearch
                    filterProps={filter}
                    processes={processes}
                    next={next}
                    prev={prev}
                    columns={cols}
                    loading={loading}
                    loadError={loadError}
                    refresh={(filters) => load(orgName, projectName, filters)}
                    showInitiatorFilter={showInitiatorFilter}
                    usePagination={usePagination}
                />
            </>
        );
    }
}

// TODO move to selectors
const makeProcessList = (data: PaginatedProcesses): ProcessEntry[] => {
    const processEntryList = Object.keys(data.processes)
        .map((k) => data.processes[k])
        .sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : 0));

    return processEntryList;
};

const mapStateToProps = ({ processes }: { processes: State }): StateProps => ({
    loading: processes.loading,
    loadError: processes.error,
    processes: makeProcessList(processes.paginatedProcessesById),
    next: processes.paginatedProcessesById.next,
    prev: processes.paginatedProcessesById.prev
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
