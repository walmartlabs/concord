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
import { isFinal, ProcessStatus, ProcessWaitHistoryEntry } from '../../../api/process';
import { get as apiGetWaits } from '../../../api/process/wait';
import { PaginationToolBar, ProcessWaitList } from '../../molecules';
import { useState } from 'react';
import RequestErrorActivity from '../RequestErrorActivity';
import { useCallback } from 'react';
import { usePolling } from '../../../api/usePolling';
import { ConcordId } from '../../../api/common';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import { Menu } from 'semantic-ui-react';

interface ExternalProps {
    instanceId: ConcordId;
    loadingHandler: (inc: number) => void;
    processStatus?: ProcessStatus;
    forceRefresh: boolean;
}

const DATA_FETCH_INTERVAL = 5000;

const ProcessWaitActivity = ({
    instanceId,
    processStatus,
    loadingHandler,
    forceRefresh
}: ExternalProps) => {
    const [data, setData] = useState<ProcessWaitHistoryEntry[]>();
    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst
    } = usePagination();
    const [next, setNext] = useState<boolean>(false);

    const fetchData = useCallback(async () => {
        const result = await apiGetWaits(
            instanceId,
            paginationFilter.offset,
            paginationFilter.limit
        );

        setData(makeProcessWaitList(result.items));
        setNext(result.next);

        return !isFinal(processStatus);
    }, [instanceId, processStatus, paginationFilter.offset, paginationFilter.limit]);

    const error = usePolling(fetchData, DATA_FETCH_INTERVAL, loadingHandler, forceRefresh);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <Menu secondary={true}>
                <Menu.Menu position={'right'}>
                    <Menu.Item>
                        <PaginationToolBar
                            limit={paginationFilter.limit}
                            handleLimitChange={(limit) => handleLimitChange(limit)}
                            handleNext={handleNext}
                            handlePrev={handlePrev}
                            handleFirst={handleFirst}
                            disablePrevious={paginationFilter.offset <= 0}
                            disableNext={!next}
                            disableFirst={paginationFilter.offset <= 0}
                        />
                    </Menu.Item>
                </Menu.Menu>
            </Menu>

            <ProcessWaitList data={data} />
        </>
    );
};

const makeProcessWaitList = (data?: ProcessWaitHistoryEntry[]): ProcessWaitHistoryEntry[] => {
    if (data === undefined) {
        return [];
    }

    return data.sort((a, b) =>
        a.eventDate < b.eventDate ? 1 : a.eventDate > b.eventDate ? -1 : 0
    );
};

export default ProcessWaitActivity;
