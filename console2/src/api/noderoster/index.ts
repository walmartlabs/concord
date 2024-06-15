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

import { fetchJson, ConcordId, queryParams, ConcordKey } from '../common';
import {
    ProcessCheckpointEntry,
    ProcessHistoryEntry,
    ProcessKind,
    ProcessMeta,
    ProcessStatus,
    TriggeredByEntry
} from '../process';

export interface HostEntry {
    id: ConcordId;
    name: string;
    createdAt: string;
    artifactUrl?: string;
}

export interface PaginatedHostEntry {
    items: HostEntry[];
    next: boolean;
}

export interface HostFilter {
    host?: string;
    processInstanceId?: ConcordId;
    artifact?: string;
}

export interface HostArtifact {
    url: string;
    processInstanceId: ConcordId;
}

export interface PaginatedHostArtifacts {
    items: HostArtifact[];
    next: boolean;
}

export interface HostProcessEntry {
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
    handlers?: string[];
    meta?: ProcessMeta;
    tags?: string[];
    checkpoints?: ProcessCheckpointEntry[];
    statusHistory?: ProcessHistoryEntry[];
    disabled: boolean;
    triggeredBy?: TriggeredByEntry;
    timeout?: number;
}

export interface PaginatedHostProcessEntry {
    items: HostProcessEntry[];
    next: boolean;
}

export const getHost = async (id: ConcordId): Promise<HostEntry> => {
    return fetchJson<HostEntry>(`/api/v1/noderoster/hosts/${id}`);
};

export type HostsInclude = 'artifacts';

export const listHosts = async (
    page: number,
    limit: number,
    includes: HostsInclude[],
    filter?: HostFilter
): Promise<PaginatedHostEntry> => {
    const offsetParam = page > 0 && limit > 0 ? page * limit : page;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: HostEntry[] = await fetchJson(
        `/api/v1/noderoster/hosts?${queryParams({
            offset: offsetParam,
            limit: limitParam,
            host: filter?.host,
            processInstanceId: filter?.processInstanceId,
            artifact: filter?.artifact,
            include: includes
        })}`
    );

    const hasMoreElements: boolean = limit > 0 && data.length > limit;

    if (limit > 0 && hasMoreElements) {
        data.pop();
    }

    return {
        items: data,
        next: hasMoreElements
    };
};

export const getLatestHostFacts = async (id: ConcordId): Promise<Object> => {
    return fetchJson<Object>(`/api/v1/noderoster/facts/last?hostId=${id}`);
};

export const listHostArtifacts = async (
    hostId: ConcordId,
    page: number,
    limit: number,
    filter?: string
): Promise<PaginatedHostArtifacts> => {
    const offsetParam = page > 0 && limit > 0 ? page * limit : page;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: HostArtifact[] = await fetchJson(
        `/api/v1/noderoster/artifacts?${queryParams({
            hostId,
            offset: offsetParam,
            limit: limitParam,
            filter
        })}`
    );

    const hasMoreElements: boolean = limit > 0 && data.length > limit;

    if (limit > 0 && hasMoreElements) {
        data.pop();
    }

    return {
        items: data,
        next: hasMoreElements
    };
};

export const listHostProcesses = async (
    hostId: ConcordId,
    page: number,
    limit: number
): Promise<PaginatedHostProcessEntry> => {
    const offsetParam = page > 0 && limit > 0 ? page * limit : page;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: HostProcessEntry[] = await fetchJson(
        `/api/v1/noderoster/processes?${queryParams({
            hostId,
            offset: offsetParam,
            limit: limitParam
        })}`
    );

    const hasMoreElements: boolean = limit > 0 && data.length > limit;

    if (limit > 0 && hasMoreElements) {
        data.pop();
    }

    return {
        items: data,
        next: hasMoreElements
    };
};
