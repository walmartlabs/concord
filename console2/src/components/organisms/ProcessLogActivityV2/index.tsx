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

import { ConcordId } from '../../../api/common';
import { isFinal, ProcessStatus } from '../../../api/process';

import './styles.css';
import { useCallback } from 'react';
import { listLogSegments as apiListLogSegments, LogSegmentEntry } from '../../../api/process/log';
import { useState } from 'react';
import { usePolling } from '../../../api/usePolling';
import RequestErrorActivity from '../RequestErrorActivity';
import LogSegmentActivity from './LogSegmentActivity';

const SEGMENT_FETCH_INTERVAL = 5000;

interface ExternalProps {
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
}
const ProcessLogActivityV2 = ({
    instanceId,
    processStatus,
    loadingHandler,
    forceRefresh
}: ExternalProps) => {
    const [segments, setSegments] = useState<LogSegmentEntry[]>([]);

    const fetchSegments = useCallback(async () => {
        // TODO: real limit/offset
        const limit = 30;
        const offset = 0;
        const segments = await apiListLogSegments(instanceId, offset, limit);
        setSegments(segments.items);
        return !isFinal(processStatus);
    }, [instanceId, processStatus]);

    const error = usePolling(fetchSegments, SEGMENT_FETCH_INTERVAL, loadingHandler, forceRefresh);
    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            {segments.map((s, index) => (
                <LogSegmentActivity
                    instanceId={instanceId}
                    segmentId={s.id}
                    correlationId={s.correlationId}
                    name={s.name}
                    status={s.status}
                    key={index}
                />
            ))}
        </>
    );
};

export default ProcessLogActivityV2;
