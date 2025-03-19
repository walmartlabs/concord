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

import { ColumnDefinition, ResourceAccessEntry } from '../';
import {
    ConcordId,
    ConcordKey,
    EntityOwner,
    fetchJson,
    GenericOperationResult,
    OperationResult,
    queryParams
} from '../../common';

export enum ProjectVisibility {
    PUBLIC = 'PUBLIC',
    PRIVATE = 'PRIVATE'
}

export interface ProjectEntryMetaUI {
    processList?: ColumnDefinition[];
    childrenProcessList?: ColumnDefinition[];
}

export interface ProjectEntryMeta {
    ui?: ProjectEntryMetaUI;
}

export enum RawPayloadMode {
    DISABLED = 'DISABLED',
    OWNERS = 'OWNERS',
    TEAM_MEMBERS = 'TEAM_MEMBERS',
    ORG_MEMBERS = 'ORG_MEMBERS',
    EVERYONE = 'EVERYONE'
}

export enum OutVariablesMode {
    DISABLED = 'DISABLED',
    OWNERS = 'OWNERS',
    TEAM_MEMBERS = 'TEAM_MEMBERS',
    ORG_MEMBERS = 'ORG_MEMBERS',
    EVERYONE = 'EVERYONE'
}

export enum ProcessExecMode {
    DISABLED = 'DISABLED',
    READERS = 'READERS',
    WRITERS = 'WRITERS'
}

export interface ProjectEntry {
    id: ConcordId;
    name: ConcordKey;

    owner: EntityOwner;

    orgId: ConcordId;
    orgName: ConcordKey;

    description?: string;
    visibility: ProjectVisibility;

    rawPayloadMode: RawPayloadMode;

    meta?: ProjectEntryMeta;

    outVariablesMode: OutVariablesMode;

    processExecMode: ProcessExecMode;
}

export interface NewProjectEntry {
    name: ConcordKey;
    description?: string;
    visibility: ProjectVisibility;
}

export interface UpdateProjectEntry {
    id?: ConcordId;
    name?: ConcordKey;
    orgId?: ConcordId;
    orgName?: ConcordKey;
    description?: string;
    visibility?: ProjectVisibility;
    rawPayloadMode?: RawPayloadMode;
    outVariablesMode?: OutVariablesMode;
    processExecMode?: ProcessExecMode;
}

export interface PaginatedProjectEntries {
    items: ProjectEntry[];
    next: boolean;
}

export interface KVCapacity {
    size: number;
    maxSize?: number;
}

export const get = (orgName: ConcordKey, projectName: ConcordKey): Promise<ProjectEntry> => {
    return fetchJson<ProjectEntry>(`/api/v2/org/${orgName}/project/${projectName}`);
};

export const getCapacity = (
    orgName: ConcordKey,
    projectName: ConcordKey
): Promise<KVCapacity> => {
    return fetchJson(`/api/v1/org/${orgName}/project/${projectName}/kv/capacity`);
};

export const list = async (
    orgName: ConcordKey,
    offset: number,
    limit: number,
    filter?: string
): Promise<PaginatedProjectEntries> => {
    const offsetParam = offset > 0 && limit > 0 ? offset * limit : offset;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: ProjectEntry[] = await fetchJson(
        `/api/v1/org/${orgName}/project?${queryParams({
            offset: offsetParam,
            limit: limitParam,
            filter
        })}`
    );

    const hasMoreElements: boolean = !!limit && data.length > limit;

    if (limit > 0 && hasMoreElements) {
        data.pop();
    }

    return {
        items: data,
        next: hasMoreElements
    };
};

export interface ProjectOperationResult {
    ok: boolean;
    id: ConcordId;
    result: OperationResult;
}

// TODO response type
export const createOrUpdate = (
    orgName: ConcordKey,
    entry: NewProjectEntry | UpdateProjectEntry
): Promise<ProjectOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entry)
    };
    return fetchJson(`/api/v1/org/${orgName}/project`, opts);
};

// TODO should we just use createOrUpdate instead?
export const rename = (
    orgName: ConcordKey,
    projectId: ConcordId,
    projectName: ConcordKey
): Promise<ProjectOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: projectId,
            name: projectName
        })
    };

    return fetchJson(`/api/v1/org/${orgName}/project`, opts);
};

// TODO should we just use createOrUpdate instead?
export const changeOwner = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    ownerId: ConcordId
): Promise<ProjectOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            name: projectName,
            owner: { id: ownerId }
        })
    };

    return fetchJson(`/api/v1/org/${orgName}/project`, opts);
};

export const deleteProject = (
    orgName: ConcordKey,
    projectName: ConcordKey
): Promise<GenericOperationResult> =>
    fetchJson(`/api/v1/org/${orgName}/project/${projectName}`, { method: 'DELETE' });

export const encrypt = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    value: string
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        body: value
    };

    return fetchJson(`/api/v1/org/${orgName}/project/${projectName}/encrypt`, opts);
};

export const getProjectAccess = (
    orgName: ConcordKey,
    projectName: ConcordKey
): Promise<GenericOperationResult> => {
    return fetchJson(`/api/v1/org/${orgName}/project/${projectName}/access`);
};

export const updateProjectAccess = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    entries: ResourceAccessEntry[]
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entries)
    };

    return fetchJson(`/api/v1/org/${orgName}/project/${projectName}/access/bulk`, opts);
};

export const getProjectConfiguration = (
    orgName: ConcordKey,
    projectName: ConcordKey
): Promise<Object> => {
    return fetchJson(`/api/v1/org/${orgName}/project/${projectName}/cfg`);
};

export const updateProjectConfiguration = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    config: Object
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(config)
    };

    return fetchJson(`/api/v1/org/${orgName}/project/${projectName}/cfg`, opts);
};
