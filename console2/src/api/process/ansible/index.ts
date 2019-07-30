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
}

export enum AnsibleStatus {
    CHANGED = 'CHANGED',
    FAILED = 'FAILED',
    OK = 'OK',
    RUNNING = 'RUNNING',
    SKIPPED = 'SKIPPED',
    UNREACHABLE = 'UNREACHABLE'
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

export interface PaginatedAnsibleHostEntries {
    items: AnsibleHost[];
    next?: number;
    prev?: number;
}

export interface AnsibleStatsEntry {
    uniqueHosts: number;
    hostGroups: string[];
    stats: any;
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
    phase: 'pre' | 'post';
    correlationId: string;
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

    const data: Promise<AnsibleHost[]> = fetchJson(
        `/api/v1/process/${instanceId}/ansible/hosts${qp}`
    );
    return data.then((hosts: AnsibleHost[]) => {
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
            next: nextPage,
            prev: prevPage
        };
    });
};

export const getAnsibleStats = (instanceId: ConcordId): Promise<AnsibleStatsEntry> =>
    fetchJson(`/api/v1/process/${instanceId}/ansible/stats`);

export const listAnsibleEvents = (
    instanceId: ConcordId,
    host?: string,
    hostGroup?: string,
    status?: string
): Promise<ProcessEventEntry<AnsibleEvent>> =>
    fetchJson(
        `/api/v1/process/${instanceId}/ansible/events?${queryParams({
            host,
            hostGroup,
            status
        })}`
    );
