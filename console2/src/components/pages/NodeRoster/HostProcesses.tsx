/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { useCallback } from 'react';

import { ConcordKey } from '../../../api/common';
import { listHostProcesses as apiList, PaginatedHostProcessEntry } from '../../../api/noderoster';
import { useApi } from '../../../hooks/useApi';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { LoadingDispatch } from '../../../App';
import { PaginationToolBar, ProcessList } from '../../molecules';
import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN
} from '../../molecules/ProcessList';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import { Menu } from 'semantic-ui-react';

export interface ExternalProps {
    hostId: ConcordKey;
    forceRefresh: any;
}

const COLUMNS = [INSTANCE_ID_COLUMN, PROJECT_COLUMN, INITIATOR_COLUMN, CREATED_AT_COLUMN];

const HostProcesses = ({ hostId, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst
    } = usePagination();

    const fetchData = useCallback(() => {
        return apiList(hostId, paginationFilter.offset, paginationFilter.limit);
    }, [hostId, paginationFilter]);

    const { data, error } = useApi<PaginatedHostProcessEntry>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <Menu secondary={true}>
                <Menu.Item style={{ padding: 0 }} position={'right'}>
                    <PaginationToolBar
                        limit={paginationFilter.limit}
                        handleLimitChange={(limit) => handleLimitChange(limit)}
                        handleNext={handleNext}
                        handlePrev={handlePrev}
                        handleFirst={handleFirst}
                        disablePrevious={paginationFilter.offset === 0}
                        disableNext={data === undefined ? true : !data.next}
                        disableFirst={paginationFilter.offset === 0}
                        disabled={data === undefined}
                    />
                </Menu.Item>
            </Menu>

            <ProcessList data={data?.items} columns={COLUMNS} />
        </>
    );
};

export default HostProcesses;
