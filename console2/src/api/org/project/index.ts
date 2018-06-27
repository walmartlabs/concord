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

import {
    ConcordId,
    ConcordKey,
    fetchJson,
    GenericOperationResult,
    OperationResult
} from '../../common';
import { RepositoryEntry } from './repository';

export enum ProjectVisibility {
    PUBLIC = 'PUBLIC',
    PRIVATE = 'PRIVATE'
}

export interface Repositories {
    [name: string]: RepositoryEntry;
}

export interface ProjectEntry {
    id: ConcordId;
    name: ConcordKey;

    orgId: ConcordId;
    orgName: ConcordKey;

    description?: string;
    visibility: ProjectVisibility;

    repositories?: Repositories;

    acceptsRawPayload: boolean;
}

export interface NewProjectEntry {
    name: ConcordKey;
    description?: string;
    visibility: ProjectVisibility;
    acceptsRawPayload?: boolean;
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
    entry: NewProjectEntry
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
export const setAcceptsRawPayload = (
    orgName: ConcordKey,
    projectId: ConcordId,
    acceptsRawPayload: boolean
): Promise<ProjectOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: projectId,
            acceptsRawPayload
        })
    };

    return fetchJson(`/api/v1/org/${orgName}/project`, opts);
};

export const deleteProject = (
    orgName: ConcordKey,
    projectName: ConcordKey
): Promise<GenericOperationResult> =>
    fetchJson(`/api/v1/org/${orgName}/project/${projectName}`, { method: 'DELETE' });
