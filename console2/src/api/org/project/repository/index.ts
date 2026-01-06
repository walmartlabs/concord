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
    queryParams,
    GenericOperationResult,
    OperationResult
} from '../../../common';

export interface RepositoryMeta {
    profiles?: string[];
    entryPoints?: string[];
}

export interface RepositoryEntry {
    id: ConcordId;
    name: ConcordKey;
    url: string;
    branch?: string;
    commitId?: string;
    path?: string;
    secretStoreType?: string;
    secretId: string;
    secretName: string;
    meta?: RepositoryMeta;
    disabled: boolean;
    triggersDisabled: boolean;
}

export interface PaginatedRepositoryEntries {
    items: RepositoryEntry[];
    next: boolean;
}

export interface EditRepositoryEntry {
    id?: ConcordId;
    name: ConcordKey;
    url: string;
    branch?: string;
    commitId?: string;
    path?: string;
    secretId: string;
    disabled: boolean;
    triggers?: TriggerEntry;
    triggersDisabled: boolean;
}

export interface TriggerCfg {
    entryPoint: string;
    name?: string;
}

export interface TriggerConditions {
    spec?: string;
    version?: string;
}

export interface TriggerEntry {
    id: ConcordId;
    eventSource: ConcordKey;
    arguments?: object;
    conditions?: TriggerConditions;
    activeProfiles?: string[];
    cfg: TriggerCfg;
}

export interface RepositoryValidationResponse {
    ok: boolean;
    result: OperationResult;
    errors?: string[];
    warnings?: string[];
}

export const get = async (
    orgName: ConcordKey,
    projectName: ConcordKey,
    repoName: ConcordKey
): Promise<RepositoryEntry> => {
    return fetchJson<RepositoryEntry>(
        `/api/v1/org/${orgName}/project/${projectName}/repository/${repoName}`
    );
};

export const list = async (
    orgName: ConcordKey,
    projectName: ConcordKey,
    offset: number,
    limit: number,
    filter?: string
): Promise<PaginatedRepositoryEntries> => {
    const offsetParam = offset > 0 && limit > 0 ? offset * limit : offset;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: RepositoryEntry[] = await fetchJson(
        `/api/v1/org/${orgName}/project/${projectName}/repository?${queryParams({
            offset: offsetParam,
            limit: limitParam,
            filter,
        })}`
    );

    const hasMoreElements: boolean = !!limit && data.length > limit;

    if (limit > 0 && hasMoreElements) {
        data.pop();
    }

    return {
        items: data,
        next: hasMoreElements,
    };
};

export const createOrUpdate = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    entry: EditRepositoryEntry
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entry)
    };

    return fetchJson(`/api/v1/org/${orgName}/project/${projectName}/repository`, opts);
};

export const deleteRepository = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    repoName: ConcordKey
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'DELETE'
    };

    return fetchJson(`/api/v1/org/${orgName}/project/${projectName}/repository/${repoName}`, opts);
};

export const refreshRepository = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    repoName: ConcordKey,
    sync: boolean
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST'
    };

    return fetchJson(
        `/api/v1/org/${orgName}/project/${projectName}/repository/${repoName}/refresh?sync=${sync}`,
        opts
    );
};

export const validateRepository = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    repoName: ConcordKey
): Promise<RepositoryValidationResponse> => {
    const opts = {
        method: 'POST'
    };

    return fetchJson(
        `/api/v1/org/${orgName}/project/${projectName}/repository/${repoName}/validate`,
        opts
    );
};

export const listTriggers = (
    orgName: ConcordKey,
    projectName: ConcordKey,
    repoName: ConcordKey
): Promise<TriggerEntry[]> =>
    fetchJson(`/api/v1/org/${orgName}/project/${projectName}/repo/${repoName}/trigger`);

export interface TriggerFilter {
    type?: ConcordKey;
    orgName?: ConcordKey;
    projectName?: ConcordKey;
    repoName?: ConcordKey;
}

export const listTriggersV2 = (filter: TriggerFilter): Promise<TriggerEntry[]> =>
    fetchJson(`/api/v2/trigger?${queryParams({ ...filter })}`);
