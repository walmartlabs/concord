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
import { ConcordId, ConcordKey, queryParams } from '../../../api/common';
import { Pagination } from '../../../state/data/processes';
import {
    list as apiProcessList,
    isFinal,
    ProcessListQuery,
    PaginatedProcessEntries,
    ProcessStatus
} from '../../../api/process';
import ProcessListWithSearch from '../../molecules/ProcessListWithSearch';
import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    STATUS_COLUMN
} from '../../molecules/ProcessList';
import { useHistory, useLocation } from 'react-router';
import { addBuiltInColumns, filtersToQuery, parseSearchFilter, ProcessSearchFilter } from '../ProcessListActivity';
import { ProcessFilters } from '../../../api/process';
import RequestErrorActivity from '../RequestErrorActivity';
import { useCallback, useEffect, useRef, useState } from 'react';
import { usePolling } from '../../../api/usePolling';
import { get as apiGetProject, ProjectEntry } from "../../../api/org/project";
import { useApi } from "../../../hooks/useApi";
import { LoadingDispatch } from "../../../App";

const COLUMNS = [
    STATUS_COLUMN,
    INSTANCE_ID_COLUMN,
    REPO_COLUMN,
    INITIATOR_COLUMN,
    CREATED_AT_COLUMN
];

interface ExternalProps {
    instanceId: ConcordId;
    loadingHandler: (inc: number) => void;
    processStatus?: ProcessStatus;
    processOrgName?: ConcordKey;
    processProjectName?: ConcordKey;
    forceRefresh: boolean;
    dataFetchInterval: number;
}

const ProcessChildrenActivity = ({
    instanceId,
    loadingHandler,
    processStatus,
    forceRefresh,
    dataFetchInterval,
    processOrgName,
    processProjectName
}: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const isInitialMount = useRef(true);
    const location = useLocation();
    const history = useHistory();
    const [data, setData] = useState<PaginatedProcessEntries>();
    const [searchFilter, setSearchFilter] = useState<ProcessSearchFilter>(
        parseSearchFilter(location.search)
    );

    const fetchProjectData = useCallback(() => {
        if (!processOrgName || !processProjectName) {
            return Promise.resolve(undefined);
        }

        return apiGetProject(processOrgName, processProjectName);
    }, [processOrgName, processProjectName]);

    const { data : project, error : projectError} = useApi<ProjectEntry | undefined>(fetchProjectData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false;
        } else {
            setSearchFilter(parseSearchFilter(location.search));
        }
    }, [location]);

    const fetchData = useCallback(async () => {
        const query = {
            parentInstanceId: instanceId,
            ...searchFilter.pagination
        } as ProcessListQuery;

        const processEntries = await apiProcessList(filtersToQuery(query, searchFilter.filters));
        setData(processEntries);

        return !isFinal(processStatus);
    }, [instanceId, searchFilter, processStatus]);

    const error = usePolling(fetchData, dataFetchInterval, loadingHandler, forceRefresh);

    const onRefresh = useCallback(
        (processFilters?: ProcessFilters, paginationFilters?: Pagination) => {
            if (processFilters || paginationFilters) {
                const f = {};
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
                // will update location
                history.push({ search: queryParams(f) });
            }
        },
        [history]
    );

    const columns = addBuiltInColumns(project?.meta?.ui?.childrenProcessList) ?? COLUMNS;

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            {projectError && <RequestErrorActivity error={projectError} />}

            <ProcessListWithSearch
                processFilters={searchFilter.filters}
                paginationFilter={searchFilter.pagination}
                processes={data?.items}
                columns={columns}
                loading={false}
                refresh={onRefresh}
                next={data?.next}
                prev={data?.prev}
                usePagination={true}
            />
        </>
    );
};

export default ProcessChildrenActivity;
