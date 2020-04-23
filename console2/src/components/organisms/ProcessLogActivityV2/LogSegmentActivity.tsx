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

import { ConcordId, RequestError } from '../../../api/common';

import './styles.css';
import { LogSegment } from '../../molecules';
import { useCallback } from 'react';
import { getSegmentLog as apiGetLog, LogRange, SegmentStatus } from '../../../api/process/log';
import { useState } from 'react';
import RequestErrorActivity from '../RequestErrorActivity';
import { LogProcessorOptions, processText } from '../../../state/data/processes/logs/processors';
import { useRef } from 'react';
import { MutableRefObject } from 'react';
import { Dispatch } from 'react';
import { SetStateAction } from 'react';
import { useEffect } from 'react';
import { isFinal, ProcessStatus } from '../../../api/process';
import { TaskCallDetails } from '../index';
import { Header, Modal } from 'semantic-ui-react';

const DATA_FETCH_INTERVAL = 5000;
const DEFAULT_RANGE: LogRange = { low: undefined, high: 2048 };

const DEFAULT_OPTS: LogProcessorOptions = {
    useLocalTime: true,
    showDate: false,
    separateTasks: true
};

interface ExternalProps {
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    segmentId: number;
    correlationId?: string;
    name: string;
    status: SegmentStatus;
}

interface FetchResponse {
    data: string;
    range: LogRange;
}

const LogSegmentActivity = ({
    instanceId,
    processStatus,
    segmentId,
    correlationId,
    name,
    status
}: ExternalProps) => {
    const range = useRef<LogRange>(DEFAULT_RANGE);
    // TODO: add opts
    const [opts, setOpts] = useState<LogProcessorOptions>(DEFAULT_OPTS);
    const [refresh, setRefresh] = useState<boolean>(false);
    const [stopPolling, setStopPolling] = useState<boolean>(true);
    const [data, setData] = useState<string[]>([]);
    const [chunkLength, setChunkLength] = useState<number>(0);
    const [segmentInfoOpen, setSegmentInfoOpen] = useState<boolean>(false);

    const fetchData = useCallback(
        async (range: LogRange) => {
            const chunk = await apiGetLog(instanceId, segmentId, range);

            const data = chunk && chunk.data.length > 0 ? chunk.data : undefined;
            const processedData = data ? processText(data, opts) : '';
            setChunkLength(chunk ? chunk.data.length : 0);

            return {
                data: processedData,
                range: chunk.range
            };
        },
        [instanceId, segmentId, opts]
    );

    const startPollingHandler = useCallback((isLoadWholeLog: boolean) => {
        if (isLoadWholeLog) {
            range.current = { low: 0 };
        } else {
            range.current = DEFAULT_RANGE;
        }
        setData([]);
        setStopPolling(false);
        setRefresh((prevState) => !prevState);
    }, []);

    const stopPollingHandler = useCallback(() => {
        setData([]);
        setStopPolling(true);
    }, []);

    const segmentInfoHandler = useCallback(() => {
        setSegmentInfoOpen(true);
    }, []);

    useEffect(() => {
        if (isFinal(processStatus)) {
            setStopPolling(true);
        }
    }, [chunkLength, processStatus]);

    const error = usePolling(fetchData, range, setData, refresh, stopPolling);
    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <LogSegment
                instanceId={instanceId}
                segmentId={segmentId}
                name={name}
                status={status}
                onStartLoading={startPollingHandler}
                onStopLoading={stopPollingHandler}
                onSegmentInfo={correlationId ? segmentInfoHandler : undefined}
                data={data}
            />

            {correlationId &&
                <Modal open={segmentInfoOpen} onClose={() => setSegmentInfoOpen(false)} size="small">
                    <Header icon="browser" content={name}/>
                    <Modal.Content>
                        <TaskCallDetails instanceId={instanceId} correlationId={correlationId}/>
                    </Modal.Content>
                </Modal>
            }
        </>
    );
};

const usePolling = (
    request: (range: LogRange) => Promise<FetchResponse>,
    rangeRef: MutableRefObject<LogRange>,
    setData: Dispatch<SetStateAction<string[]>>,
    refresh: boolean,
    stopPollingIndicator: boolean
): RequestError | undefined => {
    const poll = useRef<number | undefined>(undefined);
    const [error, setError] = useState<RequestError>();

    useEffect(() => {
        let cancelled = false;

        const fetchData = async (range: LogRange) => {
            let r = range;
            try {
                const resp = await request(r);

                r = { low: resp.range.high, high: undefined };
                rangeRef.current = r;

                setError(undefined);

                if (!cancelled && resp.data.length > 0) {
                    setData((prev) => [...prev, resp.data]);
                }
            } catch (e) {
                setError(e);
            } finally {
                if (!stopPollingIndicator && !cancelled) {
                    poll.current = setTimeout(() => fetchData(r), DATA_FETCH_INTERVAL);
                } else {
                    stopPolling();
                }
            }
        };

        if (stopPollingIndicator) {
            return;
        }

        fetchData(rangeRef.current);

        return () => {
            cancelled = true;
            stopPolling();
        };
    }, [request, setData, rangeRef, refresh, stopPollingIndicator]);

    const stopPolling = () => {
        if (poll.current) {
            clearTimeout(poll.current);
            poll.current = undefined;
        }
    };

    return error;
};

export default LogSegmentActivity;
