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

import {ConcordId, GenericOperationResult} from '../../../api/common';
import {isFinal, ProcessAttemptEntry, ProcessStatus} from '../../../api/process';

import './styles.css';
import { listLogSegments as apiListLogSegments, LogSegmentEntry } from '../../../api/process/log';
import { listAttempts as apiListAttempts } from '../../../api/process/history';
import { usePolling } from '../../../api/usePolling';
import { RequestErrorActivity } from '../../organisms';
import LogSegmentActivity from './LogSegmentActivity';
import { FormWizardAction, ProcessToolbar } from '../../molecules';
import {
    Button,
    Divider,
    Dropdown,
    DropdownDivider,
    DropdownHeader, DropdownItem,
    DropdownMenu, DropdownProps, Header, Icon,
    Menu,
    Popup,
    Radio
} from 'semantic-ui-react';
import { LogProcessorOptions } from '../../../state/data/processes/logs/processors';
import { Route } from 'react-router';
import { FormListEntry, list as apiListForms } from '../../../api/process/form';
import {statusToIcon} from "../../molecules/ProcessStatusIcon";
import {listAttempts} from "../../../api/process/history";
import {createOrUpdate as apiUpdate} from "../../../api/org/project";
import {useApi} from "../../../hooks/useApi";

const options = [
    {
        key: 1,
        text: 'Latest #5',
        value: 1,
        content: (
            <DropdownItem icon={statusToIcon['FAILED']} text={`Attempt #${1}`} />
        ),
    },
    {
        key: 2,
        text: 'Tablet',
        value: 2,
        content: (
            <DropdownItem icon={statusToIcon['FAILED']} text={`Attempt #${2}`} />
        ),
    },
    {
        key: 3,
        text: 'Desktop',
        value: 3,
        content: (
            <DropdownItem icon={statusToIcon['FAILED']} text={`Attempt #${3}`} />
        ),
    },
]

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

interface LogOptions {
    expandAllSegments: boolean;
    showSystemSegment: boolean;
    segmentOptions: LogProcessorOptions;
}

interface ExternalProps {
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    latestAttemptNumber?: number;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
    dataFetchInterval: number;
}

const ProcessLogActivityV2 = ({
    instanceId,
    processStatus,
    latestAttemptNumber,
    loadingHandler,
    forceRefresh,
    dataFetchInterval
}: ExternalProps) => {
    const [segments, setSegments] = useState<LogSegmentEntry[]>([]);
    const [logOpts, setLogOptions] = useState<LogOptions>(getStoredOpts());
    const [forms, setForms] = useState<FormListEntry[]>([]);
    const [attemptNumber, setAttemptNumber] = useState(latestAttemptNumber);

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

    // useEffect(() => {
    //     setAttemptNumber(latestAttemptNumber);
    // }, [latestAttemptNumber]);

    const attemptNumberChangeHandler = useCallback((ev: {}, { value }: DropdownProps) => {
        setAttemptNumber(value as number);
    }, []);

    const fetchSegments = useCallback(async () => {
        // TODO: real limit/offset
        const limit = -1;
        const offset = 0;
        const segments = await apiListLogSegments(instanceId, offset, limit, attemptNumber);

        setSegments(segments.items);

        return !isFinal(processStatus) && processStatus !== ProcessStatus.SUSPENDED;
    }, [instanceId, processStatus]);

    const fetchForm = useCallback(async () => {
        const forms = await apiListForms(instanceId);
        setForms(forms);

        return !isFinal(processStatus);
    }, [instanceId, processStatus]);

    const fetchAttemptsHistory = useCallback(() => {
        return apiListAttempts(instanceId);
    }, [instanceId]);

    const { error: fetchAttemptsError, isLoading: fetchAttemptsLoading, data: fetchAttemptsData } = useApi<ProcessAttemptEntry[]>(fetchAttemptsHistory, {
        fetchOnMount: true,
        forceRequest: forceRefresh
    });

    const formError = usePolling(fetchForm, dataFetchInterval, loadingHandler, forceRefresh);

    const error = usePolling(fetchSegments, dataFetchInterval, loadingHandler, forceRefresh);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    if (formError) {
        return <RequestErrorActivity error={formError} />;
    }

    if (fetchAttemptsError) {
        return <RequestErrorActivity error={fetchAttemptsError} />;
    }

    return (
        <>
            <ProcessToolbar>
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

                {fetchAttemptsData && fetchAttemptsData.length > 1 &&
                    <Dropdown
                        icon='history'
                        floating
                        labeled
                        basic
                        button
                        className='icon'
                        loading={fetchAttemptsLoading}
                        style={{ marginRight: 20, minWidth: 180 }}
                        onChange={attemptNumberChangeHandler}
                        options={
                            // [{key: latestAttemptNumber, value: latestAttemptNumber, text: <span>{processStatus && <Icon name={statusToIcon[processStatus].name} color={statusToIcon[processStatus].color}/>}{`Latest #${latestAttemptNumber}`}</span>}].concat(
                                fetchAttemptsData
                                    // .slice(0, fetchAttemptsData.length - 1)
                                    // .reverse()
                                    .map((value) => ({
                                        key: value.attemptNumber,
                                        value: value.attemptNumber,
                                        text: <span><Icon name={statusToIcon[value.status].name}
                                                          color={statusToIcon[value.status].color}/>{`Attempt #${value.attemptNumber}`}</span>
                                    }))}
                        value={attemptNumber}
                    />
                }

                <Button
                    basic
                    disabled={process === undefined}
                    onClick={() => window.open(`/api/v1/process/${instanceId}/log`, '_blank')}>
                    Raw
                </Button>
            </ProcessToolbar>

            {segments
                .filter(
                    (value) =>
                        logOpts.showSystemSegment || (!logOpts.showSystemSegment && value.id !== 0)
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
