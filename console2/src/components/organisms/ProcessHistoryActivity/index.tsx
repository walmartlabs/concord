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
import { useRef, useState } from 'react';
import { ProcessHistoryEntry, ProcessEntry, get as apiGet, isFinal } from '../../../api/process';
import { ProcessHistoryList, ProcessToolbar } from '../../molecules';
import { useCallback } from 'react';
import { usePolling } from '../../../api/usePolling';
import RequestErrorActivity from '../RequestErrorActivity';

interface ExternalProps {
    process: ProcessEntry;
}

const DATA_FETCH_INTERVAL = 5000;

const ProcessHistoryActivity = (props: ExternalProps) => {
    const stickyRef = useRef(null);

    const [process, setProcess] = useState<ProcessEntry>(props.process);
    const [data, setData] = useState<ProcessHistoryEntry[]>(props.process.statusHistory || []);

    const fetchData = useCallback(async () => {
        const process = await apiGet(props.process.instanceId, ['history']);
        setProcess(process);

        setData(makeProcessHistoryList(process));

        return !isFinal(process.status);
    }, [props.process.instanceId]);

    const [loading, error, refresh] = usePolling(fetchData, DATA_FETCH_INTERVAL);

    if (error) {
        return (
            <div ref={stickyRef}>
                <ProcessToolbar
                    stickyRef={stickyRef}
                    loading={loading}
                    refresh={refresh}
                    process={process}
                />

                <RequestErrorActivity error={error} />
            </div>
        );
    }

    return (
        <div ref={stickyRef}>
            <ProcessToolbar
                stickyRef={stickyRef}
                loading={loading}
                refresh={refresh}
                process={process}
            />

            <ProcessHistoryList data={data} />
        </div>
    );
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
