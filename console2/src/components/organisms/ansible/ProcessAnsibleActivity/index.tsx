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
import { Divider, Progress, Segment } from 'semantic-ui-react';

import {
    AnsibleEvent,
    AnsibleHost,
    AnsibleStatus,
    listAnsibleHosts as apiListAnsibleHosts,
    listAnsiblePlaybooks as apiListAnsiblePlaybooks,
    listAnsiblePlays as apiListAnsiblePlays,
    listAnsibleTasks as apiListAnsibleTasks,
    listAnsibleTaskStats as apiListAnsibleTaskStats,
    PaginatedAnsibleHostEntries,
    PlaybookInfo,
    PlayInfo,
    SearchFilter,
    TaskInfo
} from '../../../../api/process/ansible';

import { AnsibleHostList, AnsibleTaskList } from '../../../molecules';
import RequestErrorActivity from '../../RequestErrorActivity';
import { PlayInfoEntry, TaskInfoEntry } from './types';
import TaskProgressLegend from './TaskProgressLegend';
import PlayInfoList from './PlayInfoList';
import { get as apiGet, isFinal, ProcessEntry } from '../../../../api/process';
import { usePolling } from '../../../../api/usePolling';
import { ConcordId } from '../../../../api/common';

import './styles.css';
import TaskStats from './TaskStats';
import PlaybookChooser, { PlaybookEntry } from './PlaybookChooser';
import PlaybookStats, { Blocks } from './PlaybookStats';
import { ProcessEventEntry } from '../../../../api/process/event';
import { addMinutes, isBefore, parseISO as parseDate } from 'date-fns';
import { formatTimestamp } from '../../../../utils';

interface ExternalProps {
    instanceId: ConcordId;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
    dataFetchInterval: number;
}

const ANSIBLE_HOST_LIMIT = 10;

const ProcessAnsibleActivity = (props: ExternalProps) => {
    const { instanceId, loadingHandler, forceRefresh, dataFetchInterval } = props;

    const [process, setProcess] = useState<ProcessEntry>();
    const [playbooks, setPlaybooks] = useState<PlaybookInfo[]>();
    const [playbookOptions, setPlaybookOptions] = useState<PlaybookEntry[]>();
    const [selectedPlaybookId, setSelectedPlaybookId] = useState<ConcordId>();
    const [selectedBlock, setSelectedBlock] = useState<Blocks>();
    const [ansibleHosts, setAnsibleHosts] = useState<AnsibleHost[]>();
    const [ansibleHostGroups, setAnsibleHostGroups] = useState<string[]>([]);
    const [ansibleHostsNext, setAnsibleHostsNext] = useState<number>();
    const [ansibleHostsPrev, setAnsibleHostsPrev] = useState<number>();
    const [ansibleHostsFilter, setAnsibleHostsFilter] = useState({});
    const [failedAnsibleHosts, setFailedAnsibleHosts] = useState<AnsibleHost[]>();
    const [failedAnsibleHostGroups, setFailedAnsibleHostGroups] = useState<string[]>([]);
    const [failedAnsibleHostsNext, setFailedAnsibleHostsNext] = useState<number>();
    const [failedAnsibleHostsPrev, setFailedAnsibleHostsPrev] = useState<number>();
    const [failedAnsibleHostsFilter, setFailedAnsibleHostsFilter] = useState({});
    const [failedTasks, setFailedTasks] = useState<ProcessEventEntry<AnsibleEvent>[]>();
    const [playStats, setPlayStats] = useState<PlayInfoEntry[]>();
    const [taskStats, setTaskStats] = useState<TaskInfoEntry[]>();

    const [selectedPlayId, setSelectedPlayId] = useState<ConcordId>();

    const fetchAnsibleHosts = useCallback((instanceId: ConcordId, filter: SearchFilter): Promise<
        PaginatedAnsibleHostEntries
    > => {
        const limit = filter.limit || ANSIBLE_HOST_LIMIT;
        return apiListAnsibleHosts(instanceId, {
            ...filter,
            limit
        });
    }, []);

    const fetchData = useCallback(async () => {
        const process = await apiGet(instanceId, []);
        setProcess(process);

        let playbooks = await apiListAnsiblePlaybooks(instanceId);
        playbooks = playbooks.sort((a, b) =>
            a.startedAt < b.startedAt ? -1 : a.startedAt > b.startedAt ? 1 : 0
        );

        setPlaybooks(playbooks);
        setPlaybookOptions((prevState) => buildPlaybookOptions(playbooks, prevState));

        if (playbooks.length > 0 && selectedBlock !== undefined) {
            const playbookId = selectedPlaybookId || playbooks[0].id;
            const playbook = playbooks.find((p) => p.id === selectedPlaybookId) || playbooks[0];
            switch (selectedBlock) {
                case Blocks.hosts: {
                    const newFilter = { ...ansibleHostsFilter, playbookId };
                    const hosts = await fetchAnsibleHosts(instanceId, newFilter);
                    setAnsibleHosts(hosts.items);
                    setAnsibleHostGroups(hosts.hostGroups);
                    setAnsibleHostsNext(hosts.next);
                    setAnsibleHostsPrev(hosts.prev);
                    break;
                }
                case Blocks.failedHosts: {
                    const newFilter = {
                        ...failedAnsibleHostsFilter,
                        statuses: [AnsibleStatus.FAILED, AnsibleStatus.UNREACHABLE],
                        playbookId
                    };
                    const hosts = await fetchAnsibleHosts(instanceId, newFilter);
                    setFailedAnsibleHosts(hosts.items);
                    setFailedAnsibleHostGroups(hosts.hostGroups);
                    setFailedAnsibleHostsNext(hosts.next);
                    setFailedAnsibleHostsPrev(hosts.prev);
                    break;
                }
                case Blocks.plays: {
                    const plays = await apiListAnsiblePlays(instanceId, playbookId);
                    setPlayStats(makePlayStats(plays, playbook.status));

                    if (selectedPlayId !== undefined) {
                        const tasks = await apiListAnsibleTaskStats(instanceId, selectedPlayId);
                        setTaskStats(makeTaskStats(tasks));
                    }
                    break;
                }
                case Blocks.failedTasks: {
                    const tasks = await apiListAnsibleTasks(
                        instanceId,
                        playbookId,
                        undefined,
                        undefined,
                        AnsibleStatus.FAILED
                    );
                    setFailedTasks(
                        tasks
                            .filter((value) => value.data.status !== undefined)
                            .sort((a, b) =>
                                a.eventDate > b.eventDate ? 1 : a.eventDate < b.eventDate ? -1 : 0
                            )
                    );
                    break;
                }
            }
        }

        // because Ansible stats are calculated by an async process on the backend, we poll for
        // additional 10 minutes after the process finishes to make sure we got everything
        const changedRecently = isBefore(
            Date.now(),
            addMinutes(parseDate(process.lastUpdatedAt), 10)
        );

        return !isFinal(process.status) || changedRecently;
    }, [
        instanceId,
        fetchAnsibleHosts,
        selectedPlaybookId,
        selectedBlock,
        ansibleHostsFilter,
        failedAnsibleHostsFilter,
        selectedPlayId
    ]);

    const onPlaybookChangeHandler = useCallback((playbookId: ConcordId) => {
        setSelectedPlaybookId(playbookId);
        setSelectedBlock(undefined);
        setSelectedPlayId(undefined);
        setTaskStats(undefined);
        setAnsibleHosts(undefined);
        setAnsibleHostGroups([]);
        setAnsibleHostsFilter({});
        setFailedAnsibleHosts(undefined);
        setFailedAnsibleHostGroups([]);
        setFailedAnsibleHostsFilter({});
        setFailedTasks(undefined);
    }, []);

    const onBlockChangeHandler = useCallback((block: Blocks) => {
        setSelectedBlock(block);
        setSelectedPlayId(undefined);
        setTaskStats(undefined);
        setAnsibleHosts(undefined);
        setAnsibleHostGroups([]);
        setAnsibleHostsFilter({});
        setFailedAnsibleHosts(undefined);
        setFailedAnsibleHostGroups([]);
        setFailedAnsibleHostsFilter({});
        setFailedTasks(undefined);
    }, []);

    const playClickHandler = useCallback((playId: ConcordId) => {
        setSelectedPlayId(playId);
    }, []);

    useEffect(() => {
        setTaskStats(undefined);
    }, [selectedPlayId]);

    const error = usePolling(fetchData, dataFetchInterval, loadingHandler, forceRefresh);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    if (playbooks && playbooks.length === 0) {
        return (
            <Segment basic={true} style={{ padding: 0 }}>
                <div style={{ fontWeight: 'bold' }}>No data available</div>
            </Segment>
        );
    }

    const selectedPlaybook = findPlaybookOrFirst(selectedPlaybookId, playbooks);
    const playbookProgress = selectedPlaybook ? selectedPlaybook.progress : -1;

    let totalTaskWork = 0;
    if (selectedPlayId !== undefined && playStats !== undefined) {
        const p = playStats.find((p) => p.id === selectedPlayId);
        if (p !== undefined) {
            totalTaskWork = p.hostsCount;
        }
    }

    const statusColor = getStatusColor(selectedPlaybook && selectedPlaybook.status);

    return (
        <>
            <Segment basic={true} style={{ padding: 0 }}>
                <PlaybookChooser
                    currentValue={selectedPlaybook?.id}
                    options={playbookOptions}
                    onPlaybookChange={onPlaybookChangeHandler}
                />
            </Segment>

            <PlaybookStats
                hostsCount={selectedPlaybook?.hostsCount}
                failedHostsCount={selectedPlaybook?.failedHostsCount}
                playsCount={selectedPlaybook?.playsCount}
                failedTasksCount={selectedPlaybook?.failedTasksCount}
                selectedBlock={selectedBlock}
                onBlockChange={onBlockChangeHandler}
            />

            <Segment basic={true} style={{ paddingLeft: 0, paddingRight: 0, paddingBottom: 0 }}>
                <Progress
                    size={'small'}
                    percent={playbookProgress}
                    progress={'percent'}
                    active={!isFinal(process?.status) && playbookProgress < 100}
                    color={statusColor}
                />
            </Segment>

            {selectedBlock === Blocks.hosts && (
                <>
                    <Divider content="Host Stats" horizontal={true} />

                    <AnsibleHostList
                        instanceId={instanceId}
                        playbookId={selectedPlaybook?.id}
                        showStatusFilter={true}
                        hosts={ansibleHosts}
                        hostGroups={ansibleHostGroups}
                        prev={ansibleHostsPrev}
                        next={ansibleHostsNext}
                        refresh={setAnsibleHostsFilter}
                    />
                </>
            )}

            {selectedBlock === Blocks.failedHosts && (
                <>
                    <Divider content="Failed Host Stats" horizontal={true} />

                    <AnsibleHostList
                        instanceId={instanceId}
                        playbookId={selectedPlaybook?.id}
                        hosts={failedAnsibleHosts}
                        hostGroups={failedAnsibleHostGroups}
                        prev={failedAnsibleHostsPrev}
                        next={failedAnsibleHostsNext}
                        refresh={setFailedAnsibleHostsFilter}
                    />
                </>
            )}

            {selectedBlock === Blocks.plays && selectedPlaybook && (
                <>
                    <Divider content="Play Stats" horizontal={true} />

                    <PlayInfoList
                        stats={playStats}
                        playClickAction={playClickHandler}
                        playbookStatus={selectedPlaybook.status}
                        selectedPlayId={selectedPlayId}
                    />
                </>
            )}

            {selectedBlock === Blocks.plays && selectedPlayId && (
                <>
                    <Divider content="Task Stats" horizontal={true} />

                    <TaskProgressLegend loading={taskStats === undefined} />

                    <TaskStats totalTaskWork={totalTaskWork} tasks={taskStats} />
                </>
            )}

            {selectedBlock === Blocks.failedTasks && (
                <>
                    <Divider content="Failed Tasks" horizontal={true} />
                    <div style={{ overflowX: 'auto' }}>
                        <AnsibleTaskList showHosts={true} tasks={failedTasks} hidePlaybook={true} />
                    </div>
                </>
            )}
        </>
    );
};

const makePlayStats = (plays: PlayInfo[], playbookStatus: string): PlayInfoEntry[] => {
    if (plays === undefined) {
        return [];
    }

    const sorted = plays.sort((a, b) =>
        a.playOrder > b.playOrder ? 1 : a.playOrder < b.playOrder ? -1 : 0
    );

    return sorted.map((s, i) => {
        const nextPlayStarted = i + 1 < sorted.length && sorted[i + 1].finishedTaskCount > 0;
        let progress;
        if ((playbookStatus === 'OK' && s.finishedTaskCount > 0) || nextPlayStarted) {
            progress = 100;
        } else {
            const totalWork = s.hostCount * s.taskCount;
            progress =
                s.finishedTaskCount > 0 ? Math.round((s.finishedTaskCount * 100) / totalWork) : 0;
        }

        return {
            id: s.playId,
            play: s.playName,
            hostsCount: s.hostCount,
            tasksCount: s.taskCount,
            taskStats: s.taskStats,
            progress
        };
    });
};

const makeTaskStats = (tasks: TaskInfo[]): TaskInfoEntry[] => {
    if (tasks === undefined) {
        return [];
    }

    const sorted = tasks.sort((a, b) =>
        a.taskOrder > b.taskOrder ? 1 : a.taskOrder < b.taskOrder ? -1 : 0
    );

    return sorted.map((s) => {
        return {
            name: s.taskName,
            type: s.type,
            stats: {
                failed: s.failedCount,
                ok: s.okCount,
                unreachable: s.unreachableCount,
                skipped: s.skippedCount,
                running: s.runningCount
            }
        };
    });
};

const getStatusColor = (status?: string) => {
    if (status === 'OK') {
        return 'green';
    } else if (status === 'FAILED') {
        return 'red';
    }

    return undefined;
};

const findPlaybookOrFirst = (id?: ConcordId, playbooks?: PlaybookInfo[]) => {
    if (playbooks === undefined || playbooks.length === 0) {
        return undefined;
    }

    return id === undefined ? playbooks[0] : playbooks.find((p) => p.id === id) || playbooks[0];
};

const buildPlaybookOptions = (playbooks: PlaybookInfo[], oldValues?: PlaybookEntry[]) => {
    const result = playbooks.map((s) => {
        let retryInfo = '';
        if (s.retryNum && s.retryNum > 0) {
            retryInfo = ' (retry: ' + s.retryNum + ')';
        }

        return { value: s.id, text: s.name + ' @ ' + formatTimestamp(s.startedAt) + retryInfo };
    });

    if (!oldValues) {
        return result;
    }

    if (oldValues.length !== playbooks.length) {
        return result;
    }

    return oldValues;
};

export default ProcessAnsibleActivity;
