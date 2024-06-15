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

import { ConcordId, RequestError } from '../../../api/common';
import { isFinal, ProcessStatus } from '../../../api/process';
import { LogProcessorOptions, process } from '../../../state/data/processes/logs/processors';
import { LogSegment, LogSegmentType } from '../../../state/data/processes/logs/types';
import { ProcessLogViewer } from '../../molecules';

import './styles.css';
import {
    default as React,
    Dispatch,
    MutableRefObject,
    SetStateAction,
    useCallback,
    useEffect,
    useRef,
    useState
} from 'react';
import RequestErrorActivity from '../RequestErrorActivity';
import { getLog as apiGetLog, LogRange } from '../../../api/process/log';
import { useLocation } from 'react-router';

interface ExternalProps {
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
    dataFetchInterval: number;
}

interface FetchResponse {
    data: LogSegment[];
    range: LogRange;
}

const DEFAULT_RANGE: LogRange = { low: undefined, high: 2048 };

const ProcessLogActivity = ({
    instanceId,
    processStatus,
    loadingHandler,
    forceRefresh,
    dataFetchInterval
}: ExternalProps) => {
    const location = useLocation();
    const didMountRef = useRef(false);
    const range = useRef<LogRange>(DEFAULT_RANGE);
    const [data, setData] = useState<LogSegment[]>([]);
    const [opts, setOpts] = useState<LogProcessorOptions>(getStoredOpts());
    const [refresh, setRefresh] = useState<boolean>(false);
    const [stopPolling, setStopPolling] = useState<boolean>(isFinal(processStatus));
    const [wholeLogLoading, setWholeLogLoading] = useState<boolean>(location.hash !== '');
    const [selectedCorrelationId, setSelectedCorrelationId] = useState<string>(
        location.hash.substring(1)
    );

    useEffect(() => {
        if (didMountRef.current) {
            const hasHash = location.hash !== '';
            setSelectedCorrelationId(location.hash.substring(1));

            setData([]);
            setWholeLogLoading(hasHash);
            if (hasHash) {
                range.current = { low: 0 };
            } else {
                range.current = DEFAULT_RANGE;
            }
            setRefresh((prevState) => !prevState);
        }
    }, [location]);

    const optsHandler = useCallback((o: LogProcessorOptions) => {
        setData([]);
        setOpts(o);
        storeOpts(o);
        setRefresh((prevState) => !prevState);
    }, []);

    const loadWholeLog = useCallback(() => {
        setData([]);
        setWholeLogLoading(true);
        setRefresh((prevState) => !prevState);
    }, []);

    useEffect(() => {
        if (wholeLogLoading) {
            range.current = { low: 0 };
        } else {
            range.current = DEFAULT_RANGE;
        }
    }, [wholeLogLoading, opts]);

    useEffect(() => {
        setStopPolling(isFinal(processStatus));
    }, [processStatus]);

    useEffect(() => {
        if (!didMountRef.current) {
            didMountRef.current = true;
            return;
        }

        setRefresh((prevState) => !prevState);
    }, [forceRefresh]);

    const fetchData = useCallback(
        async (range: LogRange) => {
            const chunk = await apiGetLog(instanceId, range);

            const data = chunk && chunk.data.length > 0 ? chunk.data : undefined;
            const segment: LogSegment | undefined = data
                ? { data, type: LogSegmentType.DATA }
                : undefined;
            const processedData = segment ? process(segment, opts ? opts : {}) : [];

            return {
                data: processedData,
                range: chunk.range
            };
        },
        [instanceId, opts]
    );

    const error = usePolling(fetchData, range, setData, loadingHandler, refresh, stopPolling, dataFetchInterval);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <ProcessLogViewer
            instanceId={instanceId}
            processStatus={processStatus}
            data={data}
            opts={opts}
            completed={wholeLogLoading}
            optsHandler={optsHandler}
            loadWholeLog={loadWholeLog}
            selectedCorrelationId={selectedCorrelationId}
        />
    );
};

const DEFAULT_OPTS: LogProcessorOptions = {
    useLocalTime: true,
    showDate: false,
    separateTasks: true
};

const getStoredOpts = (): LogProcessorOptions => {
    const data = localStorage.getItem('logViewerOpts');
    if (!data) {
        return DEFAULT_OPTS;
    }

    return JSON.parse(data);
};

const storeOpts = (opts: LogProcessorOptions) => {
    const data = JSON.stringify(opts);
    localStorage.setItem('logViewerOpts', data);
};

const usePolling = (
    request: (range: LogRange) => Promise<FetchResponse>,
    rangeRef: MutableRefObject<LogRange>,
    setData: Dispatch<SetStateAction<LogSegment[]>>,
    loadingHandler: (inc: number) => void,
    refresh: boolean,
    stopPollingIndicator: boolean,
    dataFetchInterval: number
): RequestError | undefined => {
    const poll = useRef<number | undefined>(undefined);
    const [error, setError] = useState<RequestError>();

    useEffect(() => {
        let cancelled = false;

        const fetchData = async (range: LogRange) => {
            loadingHandler(1);

            let r = range;
            try {
                const resp = await request(r);

                r = { low: resp.range.high, high: undefined };
                rangeRef.current = r;

                setError(undefined);

                if (!cancelled) {
                    setData((prev) => [...prev, ...resp.data]);
                }
            } catch (e) {
                setError(e);
            } finally {
                if (!stopPollingIndicator && !cancelled) {
                    poll.current = window.setTimeout(() => fetchData(r), dataFetchInterval);
                } else {
                    stopPolling();
                }

                loadingHandler(-1);
            }
        };

        fetchData(rangeRef.current);

        return () => {
            cancelled = true;
            stopPolling();
        };
    }, [request, setData, rangeRef, refresh, loadingHandler, stopPollingIndicator, dataFetchInterval]);

    const stopPolling = () => {
        if (poll.current) {
            clearTimeout(poll.current);
            poll.current = undefined;
        }
    };

    return error;
};

export default ProcessLogActivity;
