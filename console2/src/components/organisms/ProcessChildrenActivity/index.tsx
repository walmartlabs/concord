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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { ConcordId, queryParams, RequestError } from '../../../api/common';
import { actions, PaginatedProcesses, Pagination, State } from '../../../state/data/processes';
import { ProcessEntry, ProcessListQuery } from '../../../api/process';
import ProcessListWithSearch from '../../molecules/ProcessListWithSearch';
import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    STATUS_COLUMN
} from '../../molecules/ProcessList';
import { replace as pushHistory } from 'connected-react-router';
import { RouteComponentProps, withRouter } from 'react-router';
import { filtersToQuery, parseSearchFilter } from '../ProcessListActivity';
import { ProcessFilters } from '../../../api/process';
import RequestErrorActivity from '../RequestErrorActivity';

const COLUMNS = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN
];

interface ExternalProps {
    instanceId: ConcordId;
}

interface RouteProps {
    status?: string;
}

interface StateProps {
    loading: boolean;
    processes: ProcessEntry[];
    loadError: RequestError;
    next?: number;
    prev?: number;
}

interface DispatchProps {
    load: (instanceId: ConcordId, filters?: ProcessFilters, pagination?: Pagination) => void;
}

type Props = StateProps & DispatchProps & ExternalProps & RouteComponentProps<RouteProps>;

class ProcessChildrenActivity extends React.Component<Props> {
    componentDidMount() {
        const { instanceId, load, location } = this.props;
        const f = parseSearchFilter(location.search);
        load(instanceId, f.filters, f.pagination);
    }

    render() {
        const { processes, instanceId, loadError, loading, history, load, next, prev } = this.props;

        if (loadError) {
            return <RequestErrorActivity error={loadError} />;
        }

        if (!processes) {
            return <h3>No processes found.</h3>;
        }

        const f = parseSearchFilter(history.location.search);
        return (
            <>
                <ProcessListWithSearch
                    processFilters={f.filters}
                    paginationFilter={f.pagination}
                    processes={processes}
                    columns={COLUMNS}
                    loading={loading}
                    loadError={loadError}
                    refresh={(processFilters, paginationFilters) =>
                        load(instanceId, processFilters, paginationFilters)
                    }
                    next={next}
                    prev={prev}
                    usePagination={true}
                />
            </>
        );
    }
}

const makeProcessList = (data: PaginatedProcesses): ProcessEntry[] => {
    return Object.keys(data.processes)
        .map((k) => data.processes[k])
        .sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : 0));
};

const mapStateToProps = ({ processes }: { processes: State }): StateProps => ({
    loading: processes.loading,
    loadError: processes.error,
    processes: makeProcessList(processes.paginatedProcessesById),
    next: processes.paginatedProcessesById.next,
    prev: processes.paginatedProcessesById.prev
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    load: (instanceId, filters?, paginationFilters?) => {
        if (filters) {
            dispatch(pushHistory({ search: queryParams(filters) }));
        }

        const query = { parentInstanceId: instanceId, ...paginationFilters } as ProcessListQuery;

        dispatch(actions.listProcesses(filtersToQuery(query, filters)));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(withRouter(ProcessChildrenActivity));
