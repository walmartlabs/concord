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
import { useCallback, useEffect, useState } from 'react';

import { ConcordId } from '../../../api/common';
import { isFinal, ProcessStatus } from '../../../api/process';

import './styles.css';
import { listLogSegments as apiListLogSegments, LogSegmentEntry } from '../../../api/process/log';
import { usePolling } from '../../../api/usePolling';
import RequestErrorActivity from '../RequestErrorActivity';
import LogSegmentActivity from './LogSegmentActivity';
import { ProcessToolbar } from '../../molecules';
import { Button, Divider, Popup, Radio } from 'semantic-ui-react';
import { LogProcessorOptions } from '../../../state/data/processes/logs/processors';

const SEGMENT_FETCH_INTERVAL = 5000;

const DEFAULT_SEGMENT_OPTS: LogProcessorOptions = {
    useLocalTime: true,
    showDate: false,
    separateTasks: true
};

const DEFAULT_OPTS: LogOptions = {
    showSystemSegment: true,
    segmentOptions: DEFAULT_SEGMENT_OPTS
};

interface LogOptions {
    showSystemSegment: boolean;
    segmentOptions: LogProcessorOptions;
}

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
    const [logOpts, setLogOptions] = useState<LogOptions>(getStoredOpts());

    const segmentOptsHandler = useCallback((o: LogProcessorOptions) => {
        setLogOptions((prev) => {
            return { ...prev, segmentOptions: o };
        });
    }, []);

    const logOptsHandler = useCallback((o: LogOptions) => {
        setLogOptions(o);
    }, []);

    useEffect(() => {
        storeOpts(logOpts);
    }, [logOpts]);

    const fetchSegments = useCallback(async () => {
        // TODO: real limit/offset
        const limit = 30;
        const offset = 0;
        const segments = await apiListLogSegments(instanceId, offset, limit);
        setSegments(segments.items);
        return !isFinal(processStatus) && processStatus !== ProcessStatus.SUSPENDED;
    }, [instanceId, processStatus]);

    const error = usePolling(fetchSegments, SEGMENT_FETCH_INTERVAL, loadingHandler, forceRefresh);
    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            <ProcessToolbar>
                <Popup
                    size="huge"
                    position="bottom left"
                    trigger={<Button basic={true} icon="setting" style={{ marginRight: 20 }} />}
                    on="click">
                    <div>
                        <Radio
                            label="Show system logs"
                            toggle={true}
                            checked={logOpts.showSystemSegment}
                            onChange={(ev, data) =>
                                logOptsHandler({
                                    ...logOpts,
                                    showSystemSegment: data.checked as boolean
                                })
                            }
                        />
                    </div>

                    <Divider horizontal={true}>Timestamps</Divider>

                    <div>
                        <Radio
                            label="Use local time"
                            toggle={true}
                            checked={logOpts.segmentOptions.useLocalTime}
                            onChange={(ev, data) =>
                                segmentOptsHandler({
                                    ...logOpts.segmentOptions,
                                    useLocalTime: data.checked as boolean
                                })
                            }
                        />
                    </div>

                    <div>
                        <Radio
                            label="Show date"
                            toggle={true}
                            checked={logOpts.segmentOptions.showDate}
                            onChange={(ev, data) =>
                                segmentOptsHandler({
                                    ...logOpts.segmentOptions,
                                    showDate: data.checked as boolean
                                })
                            }
                        />
                    </div>
                </Popup>

                <Button.Group>
                    <Button
                        disabled={process === undefined}
                        onClick={() => window.open(`/api/v1/process/${instanceId}/log`, '_blank')}>
                        Raw
                    </Button>
                </Button.Group>
            </ProcessToolbar>

            {segments
                .filter(
                    (value) =>
                        logOpts.showSystemSegment || (!logOpts.showSystemSegment && value.id !== 0)
                )
                .map((s, index) => (
                    <LogSegmentActivity
                        instanceId={instanceId}
                        segmentId={s.id}
                        correlationId={s.correlationId}
                        name={s.name}
                        status={s.status}
                        warnings={s.warnings}
                        errors={s.errors}
                        processStatus={processStatus}
                        opts={logOpts.segmentOptions}
                        forceRefresh={forceRefresh}
                        key={index}
                    />
                ))}
        </>
    );
};

const getStoredOpts = (): LogOptions => {
    const data = localStorage.getItem('logOptsV2');
    if (!data) {
        return DEFAULT_OPTS;
    }

    return JSON.parse(data);
};

const storeOpts = (opts: LogOptions) => {
    const data = JSON.stringify(opts);
    localStorage.setItem('logOptsV2', data);
};

export default ProcessLogActivityV2;
