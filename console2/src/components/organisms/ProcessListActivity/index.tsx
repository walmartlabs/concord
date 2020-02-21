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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { RouteComponentProps, withRouter } from 'react-router';
import { replace as pushHistory } from 'connected-react-router';

import { queryParams, RequestError } from '../../../api/common';
import { ProcessEntry } from '../../../api/process';
import { actions, PaginatedProcesses, Pagination, State } from '../../../state/data/processes';
import { RequestErrorMessage } from '../../molecules';
import {
    STATUS_COLUMN,
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    REPO_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN
} from '../../molecules/ProcessList';
import { ColumnDefinition } from '../../../api/org';
import ProcessListWithSearch from '../../molecules/ProcessListWithSearch';
import { ProcessFilters } from '../../../api/process';

const defaultColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN
];

const withoutProjectColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN
];

export interface ProcessSearchFilter {
    filters?: ProcessFilters;
    pagination?: Pagination;
}

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
    load: (
        orgName?: string,
        projectName?: string,
        filters?: ProcessFilters,
        pagination?: Pagination
    ) => void;
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

export const parseSearchFilter = (s: string): ProcessSearchFilter => {
    const v: any = parseQueryString(s);

    const filters: ProcessFilters = {};
    Object.keys(v)
        .filter((k) => k !== 'limit')
        .filter((k) => k !== 'offset')
        .filter((k) => v[k] !== undefined)
        .filter((k) => typeof v[k] === 'string')
        .forEach((key) => (filters[key] = v[key]));

    return {
        pagination: { limit: v.limit, offset: v.offset },
        filters
    };
};

const addBuiltInColumns = (columns?: ColumnDefinition[]): ColumnDefinition[] | undefined => {
    if (!columns) {
        return;
    }

    return columns.map((c) => {
        if (c.builtin) {
            const b = defaultColumns.find((x) => x.source === c.builtin);
            if (!b) {
                return {
                    caption: `Built-in column not found: ${c.builtin}`,
                    source: 'n/a'
                };
            }
            return b;
        }
        return c;
    });
};

class ProcessListActivity extends React.Component<Props> {
    UNSAFE_componentWillMount() {
        const { orgName, projectName, load, location } = this.props;
        const f = parseSearchFilter(location.search);
        load(orgName, projectName, f.filters, f.pagination);
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
        const cols =
            addBuiltInColumns(columns) ||
            (showProjectColumn ? defaultColumns : withoutProjectColumns);
        const f = parseSearchFilter(history.location.search);
        return (
            <>
                {loadError && <RequestErrorMessage error={loadError} />}

                <ProcessListWithSearch
                    paginationFilter={f.pagination}
                    processFilters={f.filters}
                    processes={processes}
                    next={next}
                    prev={prev}
                    columns={cols}
                    loading={loading}
                    loadError={loadError}
                    refresh={(processFilters, paginationFilters) =>
                        load(orgName, projectName, processFilters, paginationFilters)
                    }
                    showInitiatorFilter={showInitiatorFilter}
                    usePagination={usePagination}
                />
            </>
        );
    }
}

// TODO move to selectors
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
    load: (orgName?, projectName?, filters?, paginationFilters?) => {
        if (filters || paginationFilters) {
            const f = {};
            if (filters !== undefined) {
                Object.keys(filters)
                    .filter((k) => k !== undefined)
                    .forEach((key) => (f[key] = filters[key]));
            }
            if (paginationFilters !== undefined) {
                Object.keys(paginationFilters)
                    .filter((k) => k !== undefined)
                    .forEach((key) => (f[key] = paginationFilters[key]));
            }
            dispatch(pushHistory({ search: queryParams(f) }));
        }
        dispatch(actions.listProcesses(orgName, projectName, filters, paginationFilters));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(withRouter(ProcessListActivity));
