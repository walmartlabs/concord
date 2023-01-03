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
import { useState } from 'react';
import { ProcessHistoryEntry, ProcessEntry, get as apiGet, isFinal } from '../../../api/process';
import { ProcessHistoryList } from '../../molecules';
import { useCallback } from 'react';
import { usePolling } from '../../../api/usePolling';
import RequestErrorActivity from '../RequestErrorActivity';
import { ConcordId } from '../../../api/common';

interface ExternalProps {
    instanceId: ConcordId;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
}

const DATA_FETCH_INTERVAL = 5000;

const ProcessHistoryActivity = ({ instanceId, loadingHandler, forceRefresh }: ExternalProps) => {
    const [data, setData] = useState<ProcessHistoryEntry[]>();

    const fetchData = useCallback(async () => {
        const process = await apiGet(instanceId, ['history']);

        setData(makeProcessHistoryList(process));

        return !isFinal(process.status);
    }, [instanceId]);

    const error = usePolling(fetchData, DATA_FETCH_INTERVAL, loadingHandler, forceRefresh);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return <ProcessHistoryList data={data} />;
};

const makeProcessHistoryList = (process: ProcessEntry): ProcessHistoryEntry[] => {
    if (process.statusHistory === undefined) {
        return [];
    }

    return process.statusHistory.sort((a, b) =>
        a.changeDate < b.changeDate ? 1 : a.changeDate > b.changeDate ? -1 : 0
    );
};

export default ProcessHistoryActivity;
