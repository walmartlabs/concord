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

import { SemanticCOLORS } from 'semantic-ui-react';
import { ConcordId, ConcordKey, fetchJson, managedFetch } from '../common';
import { StartProcessResponse } from '../org/process';

// https://github.com/facebook/create-react-app/issues/6054
export * from './form';
export * from './log';
export * from './event';

export enum ProcessStatus {
    PREPARING = 'PREPARING',
    ENQUEUED = 'ENQUEUED',
    STARTING = 'STARTING',
    RUNNING = 'RUNNING',
    SUSPENDED = 'SUSPENDED',
    RESUMING = 'RESUMING',
    FINISHED = 'FINISHED',
    FAILED = 'FAILED',
    CANCELLED = 'CANCELLED',
    TIMED_OUT = 'TIMED_OUT'
}

export const getStatusSemanticColor = (status: ProcessStatus): SemanticCOLORS => {
    switch (status) {
        case ProcessStatus.PREPARING:
        case ProcessStatus.RUNNING:
        case ProcessStatus.STARTING:
        case ProcessStatus.SUSPENDED:
            return 'blue';
        case ProcessStatus.FINISHED:
            return 'green';
        case ProcessStatus.CANCELLED:
        case ProcessStatus.FAILED:
        case ProcessStatus.TIMED_OUT:
            return 'red';
        case ProcessStatus.ENQUEUED:
        case ProcessStatus.RESUMING:
        default:
            return 'grey';
    }
};

export const isFinal = (s: ProcessStatus) =>
    s === ProcessStatus.FINISHED ||
    s === ProcessStatus.FAILED ||
    s === ProcessStatus.CANCELLED ||
    s === ProcessStatus.TIMED_OUT;

export const hasState = (s: ProcessStatus) => s !== ProcessStatus.PREPARING;

export const canBeCancelled = (s: ProcessStatus) =>
    s === ProcessStatus.ENQUEUED || s === ProcessStatus.RUNNING || s === ProcessStatus.SUSPENDED;

export interface ProcessCheckpointEntry {
    id: string;
    name: string;
    createdAt: string;
}

export interface ProcessHistoryPayload {
    checkpointId?: string;
}

export interface ProcessHistoryEntry {
    id: ConcordId;
    payload?: ProcessHistoryPayload;
    status: ProcessStatus;
    changeDate: string;
}

export enum WaitType {
    NONE = 'NONE',
    PROCESS_COMPLETION = 'PROCESS_COMPLETION'
}

export interface ProcessWaitPayload {
    processes?: ConcordId[];
}

export interface ProcessWaitHistoryEntry {
    id: ConcordId;
    eventDate: string;
    type: WaitType;
    reason?: string;
    payload: ProcessWaitPayload;
}

export interface ProcessEntry {
    instanceId: ConcordId;
    parentInstanceId?: ConcordId;
    status: ProcessStatus;
    orgName?: ConcordKey;
    projectName?: ConcordKey;
    repoName?: ConcordKey;
    repoUrl?: string;
    repoPath?: string;
    commitId?: string;
    commitMsg?: string;
    initiator: string;
    createdAt: string;
    lastUpdatedAt: string;
    meta?: {};
    checkpoints?: ProcessCheckpointEntry[];
    statusHistory?: ProcessHistoryEntry[];
}

export const start = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    repoName: ConcordKey,
    entryPoint?: string,
    profile?: string
): Promise<StartProcessResponse> => {
    const data = new FormData();

    data.append('org', orgName);
    data.append('project', projectName);
    data.append('repo', repoName);
    if (entryPoint) {
        data.append('entryPoint', entryPoint);
    }
    if (profile) {
        data.append('activeProfiles', profile);
    }

    const opts = {
        method: 'POST',
        body: data
    };

    return fetchJson('/api/v1/process', opts);
};

export type ProcessDataInclude = Array<'checkpoints' | 'history' | 'childrenIds'>;

export const get = (instanceId: ConcordId, includes: ProcessDataInclude): Promise<ProcessEntry> => {
    const params = new URLSearchParams();
    includes.forEach((i) => params.append('include', i));
    return fetchJson(`/api/v2/process/${instanceId}?${params.toString()}`);
};

export const kill = (instanceId: ConcordId): Promise<{}> =>
    managedFetch(`/api/v1/process/${instanceId}`, { method: 'DELETE' });

export const killBulk = (instanceIds: ConcordId[]): Promise<{}> => {
    const opts = {
        method: 'DELETE',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(instanceIds)
    };
    return managedFetch('/api/v1/process/bulk', opts);
};
