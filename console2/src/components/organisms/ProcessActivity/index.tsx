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

import { Link, Redirect, Route, Switch } from 'react-router-dom';
import { Icon, Menu } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { get as apiGet, getRoot as apiGetRoot, isFinal, ProcessEntry } from '../../../api/process';
import { NotFoundPage } from '../../pages';
import {
    ProcessAnsibleActivity,
    ProcessAttachmentsActivity,
    ProcessChildrenActivity,
    ProcessEventsActivity,
    ProcessHistoryActivity,
    ProcessLogActivity,
    ProcessLogActivityV2,
    ProcessStatusActivity,
    ProcessWaitActivity
} from '../index';
import ProcessToolbar from './Toolbar';
import { usePolling } from '../../../api/usePolling';
import RequestErrorActivity from '../RequestErrorActivity';
import { useStatusFavicon } from './favicon';
import { gitUrlParse } from '../../molecules/GitHubLink';
import { useIdleTimer } from 'react-idle-timer';

export type TabLink =
    | 'status'
    | 'ansible'
    | 'log'
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

const ProcessActivity = (props: ExternalProps) => {
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
    const rootProcessRef = useRef<ProcessEntry | null>(null);

    const fetchData = useCallback(async () => {
        const process = await apiGet(props.instanceId, []);
        setProcess(process);

        if (process.parentInstanceId && !rootProcessRef.current) {
            rootProcessRef.current = await apiGetRoot(process.parentInstanceId);
        }

        return !isFinal(process.status);
    }, [props.instanceId]);

    useEffect(() => {
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
        }
    });

    const error = usePolling(fetchData, dataFetchInterval, loadingHandler, refresh);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    const { instanceId, activeTab } = props;

    const baseUrl = `/process/${instanceId}`;

    return (
        <div ref={stickyRef}>
            <ProcessToolbar
                loading={loading}
                instanceId={instanceId}
                process={process}
                rootInstanceId={rootProcessRef.current?.instanceId}
                refresh={refreshHandler}
                stickyRef={stickyRef}
            />

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

            <Switch>
                <Route path={baseUrl} exact={true}>
                    <Redirect to={`${baseUrl}/status`} />
                </Route>
                <Route path={`${baseUrl}/status`}>
                    <ProcessStatusActivity
                        instanceId={instanceId}
                        loadingHandler={loadingHandler}
                        forceRefresh={refresh}
                        refreshHandler={refreshHandler}
                        dataFetchInterval={dataFetchInterval}
                    />
                </Route>

                <Route path={`${baseUrl}/events`}>
                    <ProcessEventsActivity
                        instanceId={instanceId}
                        processStatus={process ? process.status : undefined}
                        loadingHandler={loadingHandler}
                        forceRefresh={refresh}
                        definitionLinkBase={buildDefinitionLinkBase(process)}
                        dataFetchInterval={dataFetchInterval}
                    />
                </Route>
                <Route path={`${baseUrl}/ansible`}>
                    <ProcessAnsibleActivity
                        instanceId={instanceId}
                        loadingHandler={loadingHandler}
                        forceRefresh={refresh}
                        dataFetchInterval={dataFetchInterval}
                    />
                </Route>
                <Route path={`${baseUrl}/log`} exact={true}>
                    {process &&
                        (process.runtime === 'concord-v1') && (
                            <ProcessLogActivity
                                instanceId={instanceId}
                                processStatus={process ? process.status : undefined}
                                loadingHandler={loadingHandler}
                                forceRefresh={refresh}
                                dataFetchInterval={dataFetchInterval}
                            />
                        )}
                    {process && (process.runtime === 'concord-v2' || process.runtime === undefined) && (
                        <ProcessLogActivityV2
                            instanceId={instanceId}
                            processStatus={process ? process.status : undefined}
                            loadingHandler={loadingHandler}
                            forceRefresh={refresh}
                            dataFetchInterval={dataFetchInterval}
                        />
                    )}
                </Route>
                <Route path={`${baseUrl}/history`} exact={true}>
                    <ProcessHistoryActivity
                        instanceId={instanceId}
                        loadingHandler={loadingHandler}
                        forceRefresh={refresh}
                        dataFetchInterval={dataFetchInterval}
                    />
                </Route>
                <Route path={`${baseUrl}/wait`} exact={true}>
                    <ProcessWaitActivity
                        instanceId={instanceId}
                        processStatus={process ? process.status : undefined}
                        loadingHandler={loadingHandler}
                        forceRefresh={refresh}
                        dataFetchInterval={dataFetchInterval}
                    />
                </Route>
                <Route path={`${baseUrl}/children`} exact={true}>
                    <ProcessChildrenActivity
                        instanceId={instanceId}
                        processStatus={process ? process.status : undefined}
                        loadingHandler={loadingHandler}
                        forceRefresh={refresh}
                        dataFetchInterval={dataFetchInterval}
                    />
                </Route>
                <Route path={`${baseUrl}/attachments`} exact={true}>
                    <ProcessAttachmentsActivity
                        instanceId={instanceId}
                        processStatus={process ? process.status : undefined}
                        loadingHandler={loadingHandler}
                        forceRefresh={refresh}
                        dataFetchInterval={dataFetchInterval}
                    />
                </Route>
                <Route component={NotFoundPage} />
            </Switch>
        </div>
    );
};

export default ProcessActivity;
