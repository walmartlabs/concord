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

import { SemanticCOLORS, SemanticICONS } from 'semantic-ui-react';
import { ConcordId, ConcordKey, fetchJson, managedFetch, queryParams } from '../common';
import { ColumnDefinition } from '../org';
import 'url-search-params-polyfill';

export enum ProcessStatus {
    NEW = 'NEW',
    PREPARING = 'PREPARING',
    ENQUEUED = 'ENQUEUED',
    WAITING = 'WAITING',
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
        case ProcessStatus.NEW:
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
        case ProcessStatus.WAITING:
        default:
            return 'grey';
    }
};

export const getStatusSemanticIcon = (status: ProcessStatus): SemanticICONS => {
    switch (status) {
        case ProcessStatus.NEW:
        case ProcessStatus.PREPARING:
        case ProcessStatus.RUNNING:
        case ProcessStatus.STARTING:
            return 'spinner';
        case ProcessStatus.SUSPENDED:
            return 'hourglass half';
        case ProcessStatus.FINISHED:
            return 'circle';
        case ProcessStatus.CANCELLED:
        case ProcessStatus.FAILED:
        case ProcessStatus.TIMED_OUT:
            return 'exclamation circle';
        case ProcessStatus.ENQUEUED:
        case ProcessStatus.RESUMING:
        case ProcessStatus.WAITING:
        default:
            return 'circle notch';
    }
};

export const isFinal = (s?: ProcessStatus) =>
    s === ProcessStatus.FINISHED ||
    s === ProcessStatus.FAILED ||
    s === ProcessStatus.CANCELLED ||
    s === ProcessStatus.TIMED_OUT;

export const hasState = (s: ProcessStatus) => s !== ProcessStatus.PREPARING;

export const canBeCancelled = (s: ProcessStatus) =>
    s === ProcessStatus.ENQUEUED ||
    s === ProcessStatus.RUNNING ||
    s === ProcessStatus.WAITING ||
    s === ProcessStatus.SUSPENDED;

export const canBeRestarted = (s: ProcessStatus) =>
    s === ProcessStatus.CANCELLED ||
    s === ProcessStatus.FAILED ||
    s === ProcessStatus.FINISHED ||
    s === ProcessStatus.TIMED_OUT;

export interface ProcessCheckpointEntry {
    id: string;
    name: string;
    createdAt: string;
}

export interface CheckpointRestoreHistoryEntry {
    id: number;
    checkpointId: string;
    processStatus: ProcessStatus;
    changeDate: string;
}

export interface ProcessHistoryEntry {
    id: ConcordId;
    status: ProcessStatus;
    changeDate: string;
}

export enum WaitType {
    NONE = 'NONE',
    PROCESS_COMPLETION = 'PROCESS_COMPLETION',
    PROCESS_LOCK = 'PROCESS_LOCK',
    PROCESS_SLEEP = 'PROCESS_SLEEP'
}

export interface AbstractWaitCondition {
    type: WaitType;
    reason: string;
}

export interface ProcessWaitCondition extends AbstractWaitCondition {
    processes?: ConcordId[];
}

export interface ProcessLockCondition extends AbstractWaitCondition {
    instanceId: ConcordId;
    name: string;
    scope: string;
}

export interface ProcessSleepCondition extends AbstractWaitCondition {
    until: string;
}

export type WaitCondition = ProcessWaitCondition | ProcessLockCondition | ProcessSleepCondition;

export interface ProcessWaitEntry {
    isWaiting: boolean;
    waits?: WaitCondition[];
}

export interface ProcessMeta {
    out?: {
        lastError?: {};
        [x: string]: any;
    };
    [x: string]: any;
}

export enum ProcessKind {
    DEFAULT = 'DEFAULT',
    FAILURE_HANDLER = 'FAILURE_HANDLER',
    CANCEL_HANDLER = 'CANCEL_HANDLER',
    TIMEOUT_HANDLER = 'TIMEOUT_HANDLER'
}

export type ProcessRuntime = 'concord-v1' | 'concord-v2';

export interface TriggeredByEntry {
    externalEventId?: string;
    trigger: TriggerEntry;
}

export interface TriggerEntry {
    eventSource: string;
}

export interface ProcessEntry {
    instanceId: ConcordId;
    parentInstanceId?: ConcordId;
    status: ProcessStatus;
    kind: ProcessKind;
    orgName?: ConcordKey;
    projectName?: ConcordKey;
    repoName?: ConcordKey;
    repoUrl?: string;
    repoPath?: string;
    commitId?: string;
    initiator: string;
    createdAt: string;
    startAt?: string;
    lastUpdatedAt: string;
    lastRunAt?: string;
    handlers?: string[];
    meta?: ProcessMeta;
    tags?: string[];
    checkpoints?: ProcessCheckpointEntry[];
    checkpointRestoreHistory?: CheckpointRestoreHistoryEntry[];
    statusHistory?: ProcessHistoryEntry[];
    disabled: boolean;
    triggeredBy?: TriggeredByEntry;
    timeout?: number;
    suspendTimeout?: number;
    runtime?: ProcessRuntime;
    requirements?: {};
}

export interface StartProcessResponse {
    ok: boolean;
    instanceId: string;
}

export interface RestoreProcessResponse {
    ok: boolean;
}

export const start = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    repoName: ConcordKey,
    entryPoint?: string,
    profiles?: string[],
    args?: object
): Promise<StartProcessResponse> => {
    const data = new FormData();

    data.append('org', orgName);
    data.append('project', projectName);
    data.append('repo', repoName);
    if (entryPoint) {
        data.append('entryPoint', entryPoint);
    }
    if (profiles) {
        data.append('activeProfiles', profiles.join(','));
    }

    if (args) {
        const argsBlob = [JSON.stringify({ arguments: args })];
        data.append('request', new Blob(argsBlob, { type: 'application/octet-stream' }));
    }

    const opts = {
        method: 'POST',
        body: data
    };

    return fetchJson('/api/v1/process', opts);
};

export type ProcessDataInclude = 'checkpoints' | 'history' | 'childrenIds' | 'checkpointsHistory';

export const get = (
    instanceId: ConcordId,
    includes: ProcessDataInclude[]
): Promise<ProcessEntry> => {
    const params = new URLSearchParams();
    includes.forEach((i) => params.append('include', i));
    return fetchJson(`/api/v2/process/${instanceId}?${params.toString()}`);
};

export const getRoot = (
    instanceId: ConcordId
): Promise<ProcessEntry> => {
    return fetchJson(`/api/v1/process/${instanceId}/root`);
};

export const disable = (instanceId: ConcordId, disabled: boolean): Promise<{}> =>
    managedFetch(`/api/v1/process/${instanceId}/disable/${disabled}`, { method: 'POST' });

export const kill = (instanceId: ConcordId): Promise<{}> =>
    managedFetch(`/api/v1/process/${instanceId}`, { method: 'DELETE' });

export const restart = (instanceId: ConcordId): Promise<{}> =>
    managedFetch(`/api/v1/process/${instanceId}/restart`, { method: 'POST' });

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

export interface ColumnFilter {
    column: ColumnDefinition;
    filter: string;
}

// TODO remove, use ProcessListQuery everywhere
export interface ProcessFilters {
    [source: string]: string;
}

export interface PaginatedProcessEntries {
    items: ProcessEntry[];
    next?: number;
    prev?: number;
}

export interface DateParam {
    value: string | null;
    compareType: 'ge' | 'len';
}

export interface ProcessListQuery {
    [meta: string]: any;

    orgId?: ConcordId;
    orgName?: ConcordKey;
    projectId?: ConcordId;
    projectName?: ConcordKey;
    afterCreatedAt?: string;
    beforeCreatedAt?: string;
    startAt?: DateParam;
    tags?: string[];
    status?: ProcessStatus;
    initiator?: string;
    parentInstanceId?: ConcordId;
    include?: ProcessDataInclude[];
    limit?: number;
    offset?: number;
}

const filterToQueryParams = (params: object): string => {
    let keyValues = {};

    Object.keys(params).forEach((k) => {
        const v = params[k];

        const dp = v as DateParam;
        if (dp !== undefined && v.compareType !== undefined) {
            let value = '';
            if (v.value !== null && v.value !== undefined) {
                value = v.value;
            }

            keyValues[k + '.' + v.compareType] = value;
        } else {
            keyValues[k] = v;
        }
    });

    return queryParams(keyValues, true);
};

export const list = async (q: ProcessListQuery): Promise<PaginatedProcessEntries> => {
    let { limit = 50 } = q;

    // TODO fix the CheckpointView data instead
    limit = parseInt(limit.toString());

    const requestLimit = limit + 1;

    const filters = { ...q, limit: requestLimit };
    const qp = filters ? filterToQueryParams(filters) : '';

    const data: ProcessEntry[] = await fetchJson(`/api/v2/process?${qp}`);

    const hasMoreElements: boolean = !!limit && data.length > limit;
    const offset: number = q.offset ? q.offset : 0;

    if (hasMoreElements) {
        data.pop();
    }

    const nextOffset = offset + limit;
    const prevOffset = offset - limit;
    const onFirstPage = offset === 0;

    const nextPage = hasMoreElements ? nextOffset : undefined;
    const prevPage = !onFirstPage ? prevOffset : undefined;

    return {
        items: data,
        next: nextPage,
        prev: prevPage
    };
};
