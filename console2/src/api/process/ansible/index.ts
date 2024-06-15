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

import { ConcordId, fetchJson, queryParams } from '../../common';
import { ProcessEventEntry } from '../event';

export interface SearchFilter {
    limit?: number;
    offset?: number;
    host?: string;
    hostGroup?: string;
    status?: AnsibleStatus;
    statuses?: AnsibleStatus[];
    playbookId?: ConcordId;
    sortField?: SortField;
    sortBy?: SortOrder;
}

export enum AnsibleStatus {
    CHANGED = 'CHANGED',
    FAILED = 'FAILED',
    OK = 'OK',
    RUNNING = 'RUNNING',
    SKIPPED = 'SKIPPED',
    UNREACHABLE = 'UNREACHABLE'
}

export enum SortOrder {
    ASC = 'ASC',
    DESC = 'DESC'
}

export enum SortField {
    HOST = 'HOST',
    DURATION = 'DURATION',
    STATUS = 'STATUS',
    HOST_GROUP = 'HOST_GROUP'
}

export const getStatusColor = (status: AnsibleStatus) => {
    switch (status) {
        case AnsibleStatus.OK:
            return '#5DB571';
        case AnsibleStatus.CHANGED:
            return '#00A4D3';
        case AnsibleStatus.FAILED:
            return '#EC6357';
        case AnsibleStatus.UNREACHABLE:
            return '#BDB9B9';
        case AnsibleStatus.SKIPPED:
            return '#F6BC32';
        case AnsibleStatus.RUNNING:
            return '#BDBABD';
        default:
            return '#3F3F3D';
    }
};

export interface AnsibleHost {
    host: string;
    hostGroup: string;
    status: AnsibleStatus;
    duration: number;
}

export interface AnsibleHostListResponse {
    hostGroups: string[];
    items: AnsibleHost[];
}

export interface PaginatedAnsibleHostEntries {
    items: AnsibleHost[];
    hostGroups: string[];
    next?: number;
    prev?: number;
}

export interface AnsibleEvent {
    host: string;
    hostGroup: string;
    playbook: string;
    status?: AnsibleStatus;
    task: string;
    action?: string;
    result?: object;
    ignore_errors?: boolean;
    duration?: number;
    phase: 'pre' | 'post';
    correlationId: string;
}

export interface PlaybookInfo {
    id: ConcordId;
    name: string;
    startedAt: string;
    hostsCount: number;
    failedHostsCount: number;
    playsCount: number;
    failedTasksCount: number;
    progress: number;
    status: string;
    retryNum?: number;
}

export interface PlayInfo {
    playId: ConcordId;
    playName: string;
    playOrder: number;
    hostCount: number;
    taskCount: number;
    taskStats: TaskStats;
    finishedTaskCount: number;
    flowEventCorrelationId: ConcordId;
}

export interface TaskInfo {
    taskName: string;
    type: string;
    taskOrder: number;
    okCount: number;
    failedCount: number;
    unreachableCount: number;
    skippedCount: number;
    runningCount: number;
}

export interface TaskStats {
    ok: number;
    failed: number;
    unreachable: number;
    skipped: number;
    running: number;
}

export const listAnsibleHosts = (
    instanceId: ConcordId,
    filters?: SearchFilter
): Promise<PaginatedAnsibleHostEntries> => {
    const limit = filters && filters.limit ? filters.limit : 50;
    if (filters && filters.limit) {
        filters.limit = parseInt(filters.limit.toString(), 10) + 1;
    }

    const qp = filters ? '?' + queryParams(filters) : '';

    const data: Promise<AnsibleHostListResponse> = fetchJson(
        `/api/v1/process/${instanceId}/ansible/hosts${qp}`
    );
    return data.then((resp: AnsibleHostListResponse) => {
        const hosts = resp.items;

        const hasMoreElements = limit && hosts.length > limit;
        const offset: number = filters && filters.offset ? filters.offset : 0;

        if (hasMoreElements) {
            hosts.pop();
        }

        const nextOffset = offset + parseInt(limit.toString(), 10);
        const prevOffset = offset - limit;
        const onFirstPage = offset === 0;

        const nextPage = !!hasMoreElements ? nextOffset : undefined;
        const prevPage = !onFirstPage ? prevOffset : undefined;

        return {
            items: hosts,
            hostGroups: resp.hostGroups,
            next: nextPage,
            prev: prevPage
        };
    });
};

export const listAnsibleEvents = (
    instanceId: ConcordId,
    host?: string,
    hostGroup?: string,
    status?: string,
    playbookId?: ConcordId
): Promise<ProcessEventEntry<AnsibleEvent>> =>
    fetchJson(
        `/api/v1/process/${instanceId}/ansible/events?${queryParams({
            host,
            hostGroup,
            status,
            playbookId
        })}`
    );

export const listAnsiblePlaybooks = (instanceId: ConcordId): Promise<PlaybookInfo[]> =>
    fetchJson(`/api/v1/process/${instanceId}/ansible/playbooks`);

export const listAnsiblePlays = (
    instanceId: ConcordId,
    playbookId: ConcordId
): Promise<PlayInfo[]> => fetchJson(`/api/v1/process/${instanceId}/ansible/${playbookId}/plays`);

export const listAnsibleTaskStats = (
    instanceId: ConcordId,
    playId: ConcordId
): Promise<TaskInfo[]> =>
    fetchJson(
        `/api/v1/process/${instanceId}/ansible/tasks?${queryParams({
            playId
        })}`
    );

export const listAnsibleTasks = (
    instanceId: ConcordId,
    playbookId: ConcordId,
    host?: string,
    hostGroup?: string,
    status?: string
): Promise<ProcessEventEntry<AnsibleEvent>[]> =>
    fetchJson(
        `/api/v1/process/${instanceId}/ansible/events?${queryParams({
            host,
            hostGroup,
            status,
            playbookId
        })}`
    );
