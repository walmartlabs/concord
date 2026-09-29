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
import { useCallback, useRef, useState, useEffect } from 'react';

import { Link, Navigate, Route, Routes } from 'react-router';
import { Icon, Menu } from 'semantic-ui-react';

import type { ConcordId } from '../../../api/common';
import { get as apiGet, getRoot as apiGetRoot, isFinal } from '../../../api/process';
import type { ProcessEntry } from '../../../api/process';
import NotFoundPage from '../../pages/NotFoundPage';
import RequestErrorMessage from '../../molecules/RequestErrorMessage';
import ProcessAnsibleActivity from '../ansible/ProcessAnsibleActivity';
import ProcessAttachmentsActivity from '../ProcessAttachmentsActivity';
import ProcessChildrenActivity from '../ProcessChildrenActivity';
import ProcessEventsActivity from '../ProcessEventsActivity';
import ProcessHistoryActivity from '../ProcessHistoryActivity';
import ProcessLogActivity from '../ProcessLogActivity';
import ProcessLogActivityV2 from '../ProcessLogActivityV2';
import ProcessLogTreeActivity from '../ProcessLogTreeActivity';
import ProcessStatusActivity from '../ProcessStatusActivity';
import ProcessWaitActivity from '../ProcessWaitActivity';
import ProcessToolbar from './Toolbar';
import { usePolling } from '../../../api/usePolling';
import RequestErrorActivity from '../RequestErrorActivity';
import { useStatusFavicon } from './favicon';
import { gitUrlParse } from '../../molecules/GitHubLink';
import { useIdleTimer } from 'react-idle-timer';

import './styles.css';

export type TabLink =
    | 'status'
    | 'ansible'
    | 'log'
    | 'logTree'
    | 'events'
    | 'history'
    | 'wait'
    | 'children'
    | 'attachments'
    | null;

interface ExternalProps {
    instanceId: ConcordId;
    activeTab: TabLink;
}

const DATA_FETCH_INTERVAL_ACTIVE = 5_000;
const DATA_FETCH_INTERVAL_IDLE = 60_000;
const IDLE_TIMEOUT = 1_000 * 60 * 10;

const normalizePath = (p: string) => {
    let result = p;
    if (result.endsWith('/')) {
        result = result.substring(0, result.length - 1);
    }
    if (!result.startsWith('/')) {
        result = '/' + result;
    }
    return result;
};

const buildDefinitionLinkBase = (process?: ProcessEntry) => {
    if (!process) {
        return undefined;
    }

    if (process.runtime !== 'concord-v2') {
        return undefined;
    }

    if (process.repoUrl !== undefined) {
        let link = gitUrlParse(process.repoUrl);
        if (!link) {
            return undefined;
        }

        if (link.endsWith('.git')) {
            link = link.substr(0, link.length - 4);
        }

        link += '/blob/' + process.commitId;
        if (process.repoPath) {
            link += normalizePath(process.repoPath);
        }

        return link;
    } else {
        return '/api/v1/process/' + process.instanceId + '/state/snapshot';
    }
};

const ProcessActivityView = (props: ExternalProps) => {
    const stickyRef = useRef(null);

    const [loading, setLoading] = useState<boolean>(false);
    const loadingCounter = useRef<number>(0);
    const [refresh, toggleRefresh] = useState<boolean>(false);
    const [dataFetchInterval, setDataFetchInterval] = useState(
        document.visibilityState === 'visible'
            ? DATA_FETCH_INTERVAL_ACTIVE
            : DATA_FETCH_INTERVAL_IDLE
    );

    const loadingHandler = useCallback((inc: number) => {
        loadingCounter.current += inc;
        setLoading(loadingCounter.current > 0);
    }, []);

    const [process, setProcess] = useState<ProcessEntry>();
    const retainedProcessRef = useRef<ProcessEntry>();
    const rootProcessRef = useRef<ProcessEntry | null>(null);

    const fetchData = useCallback(async () => {
        const process = await apiGet(props.instanceId, []);
        retainedProcessRef.current = process;
        setProcess(process);

        if (process.parentInstanceId && !rootProcessRef.current) {
            try {
                rootProcessRef.current = await apiGetRoot(process.parentInstanceId);
            } catch {
                rootProcessRef.current = process;
            }
        }

        return !isFinal(process.status);
    }, [props.instanceId]);

    useEffect(() => {
        retainedProcessRef.current = undefined;
        rootProcessRef.current = null;
    }, [props.instanceId]);

    useStatusFavicon(process);

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    useIdleTimer({
        timeout: IDLE_TIMEOUT,
        immediateEvents: ['visibilitychange'],
        onActive: (event?: Event) => {
            // this essentially prevents a data fetch on processes we already know are "final"
            if (process !== undefined && !isFinal(process.status)) {
                setDataFetchInterval(DATA_FETCH_INTERVAL_ACTIVE);
            }
        },
        onIdle: () => {
            setDataFetchInterval(DATA_FETCH_INTERVAL_IDLE);
        },
    });

    const error = usePolling(fetchData, dataFetchInterval, loadingHandler, refresh);
    const retainedProcess = process ?? retainedProcessRef.current;

    if (error && !retainedProcess) {
        return <RequestErrorActivity error={error} />;
    }

    const { instanceId, activeTab } = props;

    const baseUrl = `/process/${instanceId}`;

    return (
        <div ref={stickyRef}>
            <ProcessToolbar
                loading={loading}
                instanceId={instanceId}
                process={retainedProcess}
                rootInstanceId={rootProcessRef.current?.instanceId}
                refresh={refreshHandler}
                stickyRef={stickyRef}
            />

            {error && (
                <div className="ProcessPollError">
                    <RequestErrorMessage error={error} />
                    <button type="button" onClick={refreshHandler}>
                        Retry
                    </button>
                </div>
            )}

            <Menu tabular={true} style={{ marginTop: 0 }}>
                <Menu.Item active={activeTab === 'status'}>
                    <Icon name="hourglass half" />
                    <Link to={`${baseUrl}/status`}>Status</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'events'}>
                    <Icon name="content" />
                    <Link to={`${baseUrl}/events`}>Events</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'ansible'}>
                    <Icon name="chart area" />
                    <Link to={`${baseUrl}/ansible`}>Ansible</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'log'}>
                    <Icon name="book" />
                    <Link to={`${baseUrl}/log`}>Logs</Link>
                </Menu.Item>
                {retainedProcess &&
                    (retainedProcess.runtime === 'concord-v2' ||
                        retainedProcess.runtime === undefined) && (
                        <Menu.Item active={activeTab === 'logTree'}>
                            <Icon name="sitemap" />
                            <Link to={`${baseUrl}/log-tree`}>Log Tree</Link>
                        </Menu.Item>
                    )}
                <Menu.Item active={activeTab === 'history'}>
                    <Icon name="history" />
                    <Link to={`${baseUrl}/history`}>History</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'wait'}>
                    <Icon name="wait" />
                    <Link to={`${baseUrl}/wait`}>Wait Conditions</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'children'}>
                    <Icon name="chain" />
                    <Link to={`${baseUrl}/children`}>Child Processes</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'attachments'}>
                    <Icon name="paperclip" />
                    <Link to={`${baseUrl}/attachments`}>Attachments</Link>
                </Menu.Item>
            </Menu>

            <Routes>
                <Route index={true} element={<Navigate to="status" replace={true} />} />
                <Route
                    path="status"
                    element={
                        <ProcessStatusActivity
                            instanceId={instanceId}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            refreshHandler={refreshHandler}
                            dataFetchInterval={dataFetchInterval}
                        />
                    }
                />
                <Route
                    path="events"
                    element={
                        <ProcessEventsActivity
                            instanceId={instanceId}
                            processStatus={retainedProcess?.status}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            definitionLinkBase={buildDefinitionLinkBase(retainedProcess)}
                            dataFetchInterval={dataFetchInterval}
                        />
                    }
                />
                <Route
                    path="ansible"
                    element={
                        <ProcessAnsibleActivity
                            instanceId={instanceId}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            dataFetchInterval={dataFetchInterval}
                        />
                    }
                />
                <Route
                    path="log"
                    element={
                        <>
                            {retainedProcess && retainedProcess.runtime === 'concord-v1' && (
                                <ProcessLogActivity
                                    instanceId={instanceId}
                                    processStatus={retainedProcess.status}
                                    loadingHandler={loadingHandler}
                                    forceRefresh={refresh}
                                    dataFetchInterval={dataFetchInterval}
                                />
                            )}
                            {retainedProcess &&
                                (retainedProcess.runtime === 'concord-v2' ||
                                    retainedProcess.runtime === undefined) && (
                                    <ProcessLogActivityV2
                                        instanceId={instanceId}
                                        processStatus={retainedProcess.status}
                                        loadingHandler={loadingHandler}
                                        forceRefresh={refresh}
                                        dataFetchInterval={dataFetchInterval}
                                    />
                                )}
                        </>
                    }
                />
                <Route
                    path="log-tree"
                    element={
                        <ProcessLogTreeActivity
                            instanceId={instanceId}
                            processStatus={retainedProcess?.status}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            dataFetchInterval={dataFetchInterval}
                            onRefresh={refreshHandler}
                        />
                    }
                />
                <Route
                    path="history"
                    element={
                        <ProcessHistoryActivity
                            instanceId={instanceId}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            dataFetchInterval={dataFetchInterval}
                        />
                    }
                />
                <Route
                    path="wait"
                    element={
                        <ProcessWaitActivity
                            instanceId={instanceId}
                            processStatus={retainedProcess?.status}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            dataFetchInterval={dataFetchInterval}
                        />
                    }
                />
                <Route
                    path="children"
                    element={
                        <ProcessChildrenActivity
                            instanceId={instanceId}
                            processStatus={retainedProcess?.status}
                            processOrgName={retainedProcess?.orgName}
                            processProjectName={retainedProcess?.projectName}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            dataFetchInterval={dataFetchInterval}
                        />
                    }
                />
                <Route
                    path="attachments"
                    element={
                        <ProcessAttachmentsActivity
                            instanceId={instanceId}
                            processStatus={retainedProcess?.status}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            dataFetchInterval={dataFetchInterval}
                        />
                    }
                />
                <Route path="*" element={<NotFoundPage />} />
            </Routes>
        </div>
    );
};

const ProcessActivity = (props: ExternalProps) => (
    <ProcessActivityView key={props.instanceId} {...props} />
);

export default ProcessActivity;
