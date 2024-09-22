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
import {listLogSegments as apiListLogSegments, LogSegmentEntry, SegmentStatus} from '../../../api/process/log';
import { usePolling } from '../../../api/usePolling';
import { RequestErrorActivity } from '../../organisms';
import LogSegmentActivity from './LogSegmentActivity';
import { FormWizardAction, ProcessToolbar } from '../../molecules';
import {
    Button,
    Divider,
    Dropdown,
    DropdownProps,
    Popup,
    Radio,
} from 'semantic-ui-react';
import { LogProcessorOptions } from '../../../state/data/processes/logs/processors';
import { Route } from 'react-router';
import { FormListEntry, list as apiListForms } from '../../../api/process/form';

const DEFAULT_SEGMENT_OPTS: LogProcessorOptions = {
    useLocalTime: true,
    showDate: false,
    separateTasks: true
};

const DEFAULT_OPTS: LogOptions = {
    expandAllSegments: false,
    showSystemSegment: true,
    segmentOptions: DEFAULT_SEGMENT_OPTS
};

const LOG_SEGMENT_STATUS_OPTS = [
    {
        key: 'ALL',
        text: 'All logs',
        value: 'ALL',
    },
    {
        key: SegmentStatus.OK,
        text: SegmentStatus.OK,
        value: SegmentStatus.OK,
        icon: {name: 'circle', color: 'green'},
    },
    {
        key: SegmentStatus.FAILED,
        text: SegmentStatus.FAILED,
        value: SegmentStatus.FAILED,
        icon: {name: 'close', color: 'red'},
    },
    {
        key: SegmentStatus.RUNNING,
        text: SegmentStatus.RUNNING,
        value: SegmentStatus.RUNNING,
        icon: {name: 'spinner', color: 'teal'},
    },
    {
        key: SegmentStatus.SUSPENDED,
        text: SegmentStatus.SUSPENDED,
        value: SegmentStatus.SUSPENDED,
        icon: {name: 'hourglass half', color: 'blue'},
    },
]

interface LogOptions {
    expandAllSegments: boolean;
    showSystemSegment: boolean;
    segmentOptions: LogProcessorOptions;
}

interface ExternalProps {
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
    dataFetchInterval: number;
}

const ProcessLogActivityV2 = ({
    instanceId,
    processStatus,
    loadingHandler,
    forceRefresh,
    dataFetchInterval
}: ExternalProps) => {
    const [segments, setSegments] = useState<LogSegmentEntry[]>([]);
    const [logOpts, setLogOptions] = useState<LogOptions>(getStoredOpts());
    const [forms, setForms] = useState<FormListEntry[]>([]);

    const [logSegmentStatusFilter, setLogSegmentStatusFilter] = useState<string>(LOG_SEGMENT_STATUS_OPTS[0].value);

    const handleLogSegmentStatusFilterChange = useCallback((e: React.SyntheticEvent<HTMLElement>, data: DropdownProps) => {
        setLogSegmentStatusFilter(data.value as string);
    }, []);

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
        const limit = -1;
        const offset = 0;
        const segments = await apiListLogSegments(instanceId, offset, limit);

        setSegments(segments.items);

        return !isFinal(processStatus) && processStatus !== ProcessStatus.SUSPENDED;
    }, [instanceId, processStatus]);

    const fetchForm = useCallback(async () => {
        const forms = await apiListForms(instanceId);
        setForms(forms);

        return !isFinal(processStatus);
    }, [instanceId, processStatus]);

    const formError = usePolling(fetchForm, dataFetchInterval, loadingHandler, forceRefresh);

    const error = usePolling(fetchSegments, dataFetchInterval, loadingHandler, forceRefresh);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    if (formError) {
        return <RequestErrorActivity error={formError} />;
    }

    return (
        <>
            <ProcessToolbar>
                <Dropdown
                    icon='filter'
                    labeled={true}
                    button={true}
                    basic={true}
                    className='icon'
                    style={{marginRight: 20, width: 160 }}
                    options={LOG_SEGMENT_STATUS_OPTS}
                    onChange={handleLogSegmentStatusFilterChange}
                    value={logSegmentStatusFilter}
                />

                <div style={{flexGrow: 1}}/>

                {forms.length > 0 && processStatus === ProcessStatus.SUSPENDED && (
                    <div style={{ marginRight: 20 }}>
                        <Route
                            render={({ history }) => (
                                <FormWizardAction
                                    onOpenWizard={() =>
                                        history.push(
                                            `/process/${instanceId}/wizard?fullScreen=true`
                                        )
                                    }
                                />
                            )}
                        />
                    </div>
                )}

                <Popup
                    size="huge"
                    position="bottom left"
                    trigger={<Button basic={true} icon="setting" style={{ marginRight: 20 }} />}
                    on="click">
                    <div>
                        <Radio
                            label="Expand all segments"
                            toggle={true}
                            checked={logOpts.expandAllSegments}
                            onChange={(ev, data) =>
                                logOptsHandler({
                                    ...logOpts,
                                    expandAllSegments: data.checked as boolean
                                })
                            }
                        />
                    </div>

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
                .filter(
                    (value) => logSegmentStatusFilter === 'ALL' || value.status === undefined || logSegmentStatusFilter === value.status
                )
                .map((s) => {
                    return (
                        <LogSegmentActivity
                            instanceId={instanceId}
                            segmentId={s.id}
                            correlationId={s.correlationId}
                            name={s.name}
                            createdAt={s.createdAt}
                            status={s.status}
                            statusUpdatedAt={s.statusUpdatedAt}
                            warnings={s.warnings}
                            errors={s.errors}
                            processStatus={processStatus}
                            opts={logOpts.segmentOptions}
                            forceRefresh={forceRefresh}
                            key={s.id}
                            forceOpen={logOpts.expandAllSegments}
                        />
                    );
                })}
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
