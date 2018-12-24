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
import { connect, Dispatch } from 'react-redux';
import { ConcordId, queryParams, RequestError } from '../../../api/common';
import { actions } from '../../../state/data/processes/children';
import { State } from '../../../state/data/processes/children/types';
import { RequestErrorMessage } from '../../molecules';
import { ProcessEntry } from '../../../api/process';
import ProcessListWithSearch from '../../molecules/ProcessListWithSearch';
import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    UPDATED_AT_COLUMN
} from '../../molecules/ProcessList';
import { replace as pushHistory } from 'react-router-redux';
import { RouteComponentProps, withRouter } from 'react-router';
import { PaginatedProcesses } from '../../../state/data/processes';
import { parseSearchFilter } from '../ProcessListActivity';
import { PaginationFilter } from '../../molecules/Pagination';
import { ProcessFilters } from '../../../api/org/process';

const COLUMNS = [
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN
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
    load: (instanceId: ConcordId, filters?: ProcessFilters, pagination?: PaginationFilter) => void;
}

type Props = StateProps & DispatchProps & ExternalProps & RouteComponentProps<RouteProps>;

class ProcessChildrenActivity extends React.Component<Props> {
    constructor(props: Props) {
        super(props);
    }

    componentDidMount() {
        const { instanceId, load, location } = this.props;
        const f = parseSearchFilter(location.search);
        load(instanceId, f.filters, f.pagination);
    }

    render() {
        const { processes, instanceId, loadError, loading, history, load, next, prev } = this.props;

        if (loadError) {
            return <RequestErrorMessage error={loadError} />;
        }

        if (!processes) {
            return <h3>No processes found.</h3>;
        }

        const f = parseSearchFilter(history.location.search);
        return (
            <>
                {loadError && <RequestErrorMessage error={loadError} />}

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

interface StateType {
    processes: {
        children: State;
    };
}

export const mapStateToProps = ({ processes: { children } }: StateType): StateProps => ({
    loading: children.loading,
    loadError: children.error,
    processes: makeProcessList(children.listChildren),
    next: children.listChildren.next,
    prev: children.listChildren.prev
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (instanceId, filters?, paginationFilters?) => {
        if (filters) {
            dispatch(pushHistory({ search: queryParams(filters) }));
        }
        dispatch(actions.listChildren(instanceId, filters));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(withRouter(ProcessChildrenActivity));
