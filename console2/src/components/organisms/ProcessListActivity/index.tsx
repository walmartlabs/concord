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
import { useLocation } from 'react-router';
import { useHistory } from '@/router';

import { queryParams } from '../../../api/common';
import {
    list as apiProcessList,
    PaginatedProcessEntries,
    ProcessFilters,
    ProcessListQuery,
} from '../../../api/process';
import { Pagination } from '../../molecules/PaginationToolBar/usePagination';
import {
    CREATED_AT_COLUMN,
    DURATION_COLUMN,
    ENTRY_POINT_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    REPO_COLUMN,
    Status,
    STATUS_COLUMN,
    UPDATED_AT_COLUMN,
} from '../../molecules/ProcessList';
import { ColumnDefinition } from '../../../api/org';
import ProcessListWithSearch from '../../molecules/ProcessListWithSearch';
import RequestErrorActivity from '../RequestErrorActivity';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useApi } from '../../../hooks/useApi';
import { LoadingDispatch } from '../../../App';
import { deepEqual } from '../../../utils';

// list of "built-in" columns, i.e. columns that can be referenced using "builtin" parameter
// of the custom column configuration
const builtInColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    DURATION_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
    UPDATED_AT_COLUMN,
    ENTRY_POINT_COLUMN,
];

// list of columns visible by default
const defaultColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    DURATION_COLUMN,
    PROJECT_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
];

// list of columns visible by default for views without the project column
const withoutProjectColumns = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN,
];

export interface ProcessSearchFilter {
    filters?: ProcessFilters;
    pagination?: Pagination;
}

const parseQueryString = (s: string): Record<string, string | string[]> => {
    const result: Record<string, string | string[]> = {};

    new URLSearchParams(s).forEach((value, key) => {
        const existing = result[key];
        if (existing === undefined) {
            result[key] = value;
        } else if (Array.isArray(existing)) {
            existing.push(value);
        } else {
            result[key] = [existing, value];
        }
    });

    return result;
};

interface ExternalProps {
    orgName?: string;
    projectName?: string;
    showInitiatorFilter?: boolean;
    usePagination?: boolean;
    columns?: ColumnDefinition[];
    forceRefresh?: any;
}

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
        filters,
    };
};

export const addBuiltInColumns = (columns?: ColumnDefinition[]): ColumnDefinition[] | undefined => {
    if (!columns) {
        return;
    }

    return columns.map((c) => {
        if (c.builtin) {
            const b = builtInColumns.find(
                (x) => x.source === c.builtin || x.source === 'meta.' + c.builtin
            );
            if (!b) {
                return {
                    caption: `Built-in column not found: ${c.builtin}`,
                    source: 'n/a',
                };
            }
            return b;
        }
        return c;
    });
};

const ProcessListActivity = ({
    showInitiatorFilter = false,
    usePagination = false,
    columns,
    orgName,
    projectName,
    forceRefresh,
}: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const isInitialMount = useRef(true);
    const location = useLocation();
    const history = useHistory();
    const [searchFilter, setSearchFilter] = useState<ProcessSearchFilter>(
        parseSearchFilter(location.search)
    );

    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false;
        } else {
            const filter = parseSearchFilter(location.search);
            setSearchFilter((prev) => (deepEqual(filter, prev) ? prev : filter));
        }
    }, [location]);

    const fetchData = useCallback(() => {
        const query = {
            orgName,
            projectName,
            ...searchFilter.pagination,
        } as ProcessListQuery;

        return apiProcessList(filtersToQuery(query, searchFilter.filters));
    }, [orgName, projectName, searchFilter]);

    const { error, isLoading, data } = useApi<PaginatedProcessEntries>(fetchData, {
        fetchOnMount: true,
        dispatch: dispatch,
        forceRequest: forceRefresh,
    });

    const onRefresh = useCallback(
        (processFilters?: ProcessFilters, paginationFilters?: Pagination) => {
            const f = {};
            if (processFilters || paginationFilters) {
                if (processFilters !== undefined) {
                    Object.keys(processFilters)
                        .filter((k) => k !== undefined)
                        .forEach((key) => (f[key] = processFilters[key]));
                }
                if (paginationFilters !== undefined) {
                    Object.keys(paginationFilters)
                        .filter((k) => k !== undefined)
                        .forEach((key) => (f[key] = paginationFilters[key]));
                }
            }
            setSearchFilter(parseSearchFilter(queryParams(f)));
            // will update location
            history.push({ search: queryParams(f) });
        },
        [history]
    );

    const showProjectColumn = !projectName;
    const cols =
        addBuiltInColumns(columns) || (showProjectColumn ? defaultColumns : withoutProjectColumns);
    const f = parseSearchFilter(location.search);

    return (
        <>
            {error && <RequestErrorActivity error={error} />}

            <ProcessListWithSearch
                paginationFilter={f.pagination}
                processFilters={f.filters}
                processes={data?.items}
                next={data?.next}
                prev={data?.prev}
                columns={cols}
                loading={isLoading}
                refresh={onRefresh}
                showInitiatorFilter={showInitiatorFilter}
                usePagination={usePagination}
            />
        </>
    );
};

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

export default ProcessListActivity;
