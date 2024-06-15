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
    EntityOwner,
    queryParams,
    OperationResult
} from '../../common';
import { ResourceAccessEntry } from '../index';

export enum StorageVisibility {
    PUBLIC = 'PUBLIC',
    PRIVATE = 'PRIVATE'
}

export interface StorageEntry {
    id: ConcordId;
    name: ConcordKey;

    orgId: ConcordId;
    orgName: ConcordKey;

    projectId: ConcordId;
    projectName: ConcordKey;

    visibility: StorageVisibility;

    owner?: EntityOwner;
}

export interface StorageCapacity {
    size?: number;
    maxSize?: number;
}

export interface StorageOperationResult {
    ok: boolean;
    id: ConcordId;
    result: OperationResult;
}

export interface PaginatedStorageEntries {
    items: StorageEntry[];
    next: boolean;
}

export interface Pagination {
    limit: number;
    offset: number;
}

export type StorageDataEntry = string;

export interface PaginatedStorageDataEntries {
    items: StorageDataEntry[];
    next: boolean;
}

export interface StorageQueryEntry {
    name: ConcordKey;
    text: string;
}

export interface PaginatedStorageQueryEntries {
    items: StorageQueryEntry[];
    next: boolean;
}

export const get = (orgName: ConcordKey, storeName: ConcordKey): Promise<StorageEntry> => {
    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}`);
};

export const getCapacity = (
    orgName: ConcordKey,
    storeName: ConcordKey
): Promise<StorageCapacity> => {
    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}/capacity`);
};

export const list = async (
    orgName: ConcordKey,
    offset: number,
    limit: number,
    filter?: string
): Promise<PaginatedStorageEntries> => {
    const offsetParam = offset > 0 && limit > 0 ? offset * limit : offset;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: StorageEntry[] = await fetchJson(
        `/api/v1/org/${orgName}/jsonstore?${queryParams({
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

export const createOrUpdate = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    visibility?: StorageVisibility,
    newOrgName?: ConcordKey
): Promise<StorageOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            name: storeName,
            orgName: newOrgName,
            visibility
        })
    };
    return fetchJson(`/api/v1/org/${orgName}/jsonstore`, opts);
};

export const deleteStorage = (
    orgName: ConcordKey,
    storeName: ConcordKey
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'DELETE'
    };

    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}`, opts);
};

export const updateVisibility = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    visibility: StorageVisibility
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            name: storeName,
            visibility
        })
    };

    return fetchJson(`/api/v1/org/${orgName}/jsonstore`, opts);
};

export const changeOwner = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    ownerId: ConcordId
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            name: storeName,
            owner: { id: ownerId }
        })
    };

    return fetchJson(`/api/v1/org/${orgName}/jsonstore`, opts);
};

export const getAccess = (
    orgName: ConcordKey,
    storeName: ConcordKey
): Promise<ResourceAccessEntry[]> => {
    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}/access`);
};

export const updateAccess = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    entries: ResourceAccessEntry[]
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entries)
    };

    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}/access/bulk`, opts);
};

export const listStorageData = async (
    orgName: ConcordKey,
    storeName: ConcordKey,
    offset: number,
    limit: number,
    filter?: string
): Promise<PaginatedStorageDataEntries> => {
    const offsetParam = offset > 0 && limit > 0 ? offset * limit : offset;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: StorageDataEntry[] = await fetchJson(
        `/api/v1/org/${orgName}/jsonstore/${storeName}/item?${queryParams({
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

export const deleteStorageData = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    storagePath: string
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'DELETE'
    };
    const escapedPath = escapeStoragePath(storagePath);
    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}/item/${escapedPath}`, opts);
};

export const escapeStoragePath = (s: string): string => s.replace(/\//g, '%2F');

export const listStorageQuery = async (
    orgName: ConcordKey,
    storeName: ConcordKey,
    offset: number,
    limit: number,
    filter?: string
): Promise<PaginatedStorageQueryEntries> => {
    const offsetParam = offset > 0 && limit > 0 ? offset * limit : offset;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: StorageQueryEntry[] = await fetchJson(
        `/api/v1/org/${orgName}/jsonstore/${storeName}/query?${queryParams({
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

export const deleteStorageQuery = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    storageQueryName: string
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'DELETE'
    };
    return fetchJson(
        `/api/v1/org/${orgName}/jsonstore/${storeName}/query/${storageQueryName}`,
        opts
    );
};

export const createOrUpdateStorageQuery = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    queryName: ConcordKey,
    query: string
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            name: queryName,
            text: query
        })
    };
    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}/query`, opts);
};

export const getStorageQuery = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    queryName: ConcordKey
): Promise<StorageQueryEntry> => {
    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}/query/${queryName}`);
};

export const executeQuery = (
    orgName: ConcordKey,
    storeName: ConcordKey,
    query: string
): Promise<Object> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain'
        },
        body: query
    };
    return fetchJson(`/api/v1/org/${orgName}/jsonstore/${storeName}/execQuery?maxLimit=50`, opts);
};
