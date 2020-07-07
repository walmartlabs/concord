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
import { ProcessEntry, ProcessFilters, ProcessListQuery } from '../../../api/process';
import { actions, PaginatedProcesses, Pagination, State } from '../../../state/data/processes';
import {
    CREATED_AT_COLUMN,
    DURATION_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    REPO_COLUMN,
    Status,
    STATUS_COLUMN,
    UPDATED_AT_COLUMN
} from '../../molecules/ProcessList';
import { ColumnDefinition } from '../../../api/org';
import ProcessListWithSearch from '../../molecules/ProcessListWithSearch';
import RequestErrorActivity from '../RequestErrorActivity';

// list of "built-in" columns, i.e. columns that can be referenced using "builtin" parameter
// of the custom column configuration
const builtInColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    DURATION_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN
];

// list of columns visible by default
const defaultColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    DURATION_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN
];

// list of columns visible by default for views without the project column
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
        pagination: { limit: Number(v.limit) || undefined, offset: Number(v.offset) || undefined },
        filters
    };
};

const addBuiltInColumns = (columns?: ColumnDefinition[]): ColumnDefinition[] | undefined => {
    if (!columns) {
        return;
    }

    return columns.map((c) => {
        if (c.builtin) {
            const b = builtInColumns.find((x) => x.source === c.builtin);
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
            return <RequestErrorActivity error={loadError} />;
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
                <ProcessListWithSearch
                    paginationFilter={f.pagination}
                    processFilters={f.filters}
                    processes={processes}
                    next={next}
                    prev={prev}
                    columns={cols}
                    loading={loading}
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

        const query = { orgName, projectName, ...paginationFilters } as ProcessListQuery;

        dispatch(actions.listProcesses(filtersToQuery(query, filters)));
    }
});

export const filtersToQuery = (
    query: ProcessListQuery,
    filters?: ProcessFilters
): ProcessListQuery => {
    if (!filters) {
        return query;
    }

    Object.keys(filters).forEach((key) => {
        if (key === STATUS_COLUMN.source && filters[key] === 'SCHEDULED') {
            query[key] = Status.ENQUEUED;
            query.startAt = { compareType: 'ge', value: null };
        } else if (key === STATUS_COLUMN.source && filters[key] === 'ENQUEUED') {
            query[key] = Status.ENQUEUED;
            query.startAt = { compareType: 'len', value: null };
        } else {
            query[key] = filters[key];
        }
    });

    return query;
};

export default connect(mapStateToProps, mapDispatchToProps)(withRouter(ProcessListActivity));
