/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import { ConcordId, ConcordKey, fetchJson } from '../../common';
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

// TODO response type
export const createOrUpdate = (orgName: ConcordKey, entry: NewProjectEntry) => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entry)
    };

    return fetchJson(`/api/v1/org/${orgName}/project`, opts);
};
