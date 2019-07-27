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
    Owner
} from '../../common';
import { RepositoryEntry } from './repository';

export enum ProjectVisibility {
    PUBLIC = 'PUBLIC',
    PRIVATE = 'PRIVATE'
}

export interface Repositories {
    [name: string]: RepositoryEntry;
}

export interface ProjectEntryMetaUI {
    processList?: ColumnDefinition[];
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

export interface ProjectEntry {
    id: ConcordId;
    name: ConcordKey;

    owner: EntityOwner;

    orgId: ConcordId;
    orgName: ConcordKey;

    description?: string;
    visibility: ProjectVisibility;

    repositories?: Repositories;

    rawPayloadMode: RawPayloadMode;

    meta?: ProjectEntryMeta;
}

export interface NewProjectEntry {
    name: ConcordKey;
    description?: string;
    visibility: ProjectVisibility;
}

export interface UpdateProjectEntry {
    id?: ConcordId;
    name?: ConcordKey;
    description?: string;
    visibility?: ProjectVisibility;
    rawPayloadMode?: RawPayloadMode;
}

export const get = (orgName: ConcordKey, projectName: ConcordKey): Promise<ProjectEntry> => {
    return fetchJson<ProjectEntry>(`/api/v1/org/${orgName}/project/${projectName}`);
};

export const list = (orgName: ConcordKey): Promise<ProjectEntry[]> =>
    fetchJson<ProjectEntry[]>(`/api/v1/org/${orgName}/project`);

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
    projectId: ConcordId,
    owner: Owner
): Promise<ProjectOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: projectId,
            owner: { username: owner.username, userDomain: owner.userDomain }
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
