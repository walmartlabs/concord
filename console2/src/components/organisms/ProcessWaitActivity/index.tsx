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
import { isFinal, ProcessStatus, WaitCondition } from '../../../api/process';
import { get as apiGetWaits } from '../../../api/process/wait';
import { ProcessWaitList } from '../../molecules';
import { useState } from 'react';
import RequestErrorActivity from '../RequestErrorActivity';
import { useCallback } from 'react';
import { usePolling } from '../../../api/usePolling';
import { ConcordId } from '../../../api/common';

interface ExternalProps {
    instanceId: ConcordId;
    loadingHandler: (inc: number) => void;
    processStatus?: ProcessStatus;
    forceRefresh: boolean;
    dataFetchInterval: number;
}

const ProcessWaitActivity = ({
    instanceId,
    processStatus,
    loadingHandler,
    forceRefresh,
    dataFetchInterval
}: ExternalProps) => {
    const [waitConditions, setWaitConditions] = useState<WaitCondition[] | undefined>(undefined);

    const fetchData = useCallback(async () => {
        const result = await apiGetWaits(instanceId);
        setWaitConditions(result?.waits || []);

        return !isFinal(processStatus);
    }, [instanceId, processStatus]);

    const error = usePolling(fetchData, dataFetchInterval, loadingHandler, forceRefresh);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <ProcessWaitList data={waitConditions} />
        </>
    );
};

export default ProcessWaitActivity;
