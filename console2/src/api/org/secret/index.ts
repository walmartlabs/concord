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

import { CreateSecretResponse } from '../../../state/data/secrets/types';
import {
    ConcordId,
    ConcordKey,
    fetchJson,
    GenericOperationResult,
    EntityOwner,
    queryParams
} from '../../common';
import { ResourceAccessEntry } from '../';

export enum SecretVisibility {
    PUBLIC = 'PUBLIC',
    PRIVATE = 'PRIVATE'
}

export enum SecretType {
    KEY_PAIR = 'KEY_PAIR',
    USERNAME_PASSWORD = 'USERNAME_PASSWORD',
    DATA = 'DATA'
}

export enum SecretTypeExt {
    NEW_KEY_PAIR,
    EXISTING_KEY_PAIR,
    USERNAME_PASSWORD,
    VALUE_STRING,
    VALUE_FILE
}

export enum SecretEncryptedByType {
    SERVER_KEY = 'SERVER_KEY',
    PASSWORD = 'PASSWORD'
}

export enum SecretStoreType {
    CONCORD = 'CONCORD',
    KEYWHIZ = 'KEYWHIZ'
}

export interface SecretEntry {
    id: ConcordId;
    name: ConcordKey;

    orgId: ConcordId;
    orgName: ConcordKey;

    projectId: ConcordId;
    projectName: ConcordKey;

    visibility: SecretVisibility;
    type: SecretType;
    encryptedBy: SecretEncryptedByType;

    storeType: SecretStoreType;

    owner?: EntityOwner;
}

export interface NewSecretEntry {
    name: string;
    visibility: SecretVisibility;
    projectName?: ConcordKey;
    type: SecretTypeExt;
    publicFile?: File;
    privateFile?: File;
    username?: string;
    password?: string;
    valueString?: string;
    valueFile?: File;
    generatePassword?: boolean;
    storePassword?: string;
    storeType?: SecretStoreType;
}

export interface PaginatedSecretEntries {
    items: SecretEntry[];
    next?: boolean;
}

export interface Pagination {
    limit: number;
    offset: number;
}

export interface PublicKeyResponse {
    publicKey: string;
}

export const get = (orgName: ConcordKey, secretName: ConcordKey): Promise<SecretEntry> => {
    return fetchJson(`/api/v1/org/${orgName}/secret/${secretName}`);
};

export const list = async (
    orgName: ConcordKey,
    offset: number,
    limit: number,
    filter?: string
): Promise<PaginatedSecretEntries> => {
    const offsetParam = offset > 0 && limit > 0 ? offset * limit : offset;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: SecretEntry[] = await fetchJson(
        `/api/v1/org/${orgName}/secret?${queryParams({
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

export const deleteSecret = (
    orgName: ConcordKey,
    secretName: ConcordKey
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'DELETE'
    };

    return fetchJson(`/api/v1/org/${orgName}/secret/${secretName}`, opts);
};

export const renameSecret = (
    orgName: ConcordKey,
    secretId: ConcordId,
    secretName: ConcordKey,
    projectId: ConcordId
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: secretId,
            name: secretName,
            projectId
        })
    };

    // TODO placeholder name as a workaround for swagger-maven-plugin issue
    return fetchJson(`/api/v1/org/${orgName}/secret/todo`, opts);
};

export const updateSecretVisibility = (
    orgName: ConcordKey,
    secretId: ConcordId,
    projectId: ConcordId,
    visibility: SecretVisibility
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: secretId,
            projectId,
            visibility
        })
    };

    // TODO placeholder name as a workaround for swagger-maven-plugin issue
    return fetchJson(`/api/v1/org/${orgName}/secret/todo`, opts);
};

export const updateSecretProject = (
    orgName: ConcordKey,
    secretName: ConcordKey,
    projectName: ConcordKey
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            projectName
        })
    };

    return fetchJson(`/api/v1/org/${orgName}/secret/${secretName}`, opts);
};

// TODO response type
export const create = (
    orgName: ConcordKey,
    entry: NewSecretEntry
): Promise<CreateSecretResponse> => {
    const data = new FormData();

    data.append('name', entry.name);
    data.append('visibility', entry.visibility);

    switch (entry.type) {
        case SecretTypeExt.NEW_KEY_PAIR: {
            data.append('type', SecretType.KEY_PAIR);
            break;
        }
        case SecretTypeExt.EXISTING_KEY_PAIR: {
            data.append('type', SecretType.KEY_PAIR);
            data.append('public', entry.publicFile!);
            data.append('private', entry.privateFile!);
            break;
        }
        case SecretTypeExt.USERNAME_PASSWORD: {
            data.append('type', SecretType.USERNAME_PASSWORD);
            data.append('username', entry.username!);
            data.append('password', entry.password!);
            break;
        }
        case SecretTypeExt.VALUE_STRING: {
            data.append('type', SecretType.DATA);
            data.append('data', entry.valueString!);
            break;
        }
        case SecretTypeExt.VALUE_FILE: {
            data.append('type', SecretType.DATA);
            data.append('data', entry.valueFile!);
            break;
        }
        default: {
            return Promise.reject(`Unsupported secret type: ${entry.type}`);
        }
    }

    if (entry.generatePassword) {
        data.append('generatePassword', 'true');
    } else if (entry.storePassword) {
        data.append('storePassword', entry.storePassword);
    }

    if (entry.storeType) {
        data.append('storeType', entry.storeType);
    }

    if (entry.projectName) {
        data.append('project', entry.projectName);
    }

    const opts = {
        method: 'POST',
        body: data
    };

    return fetchJson(`/api/v1/org/${orgName}/secret`, opts);
};

export const changeOrganization = (
    orgName: ConcordKey,
    secretName: ConcordKey,
    newOrgId: ConcordId
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            orgId: newOrgId
        })
    };

    return fetchJson(`/api/v1/org/${orgName}/secret/${secretName}`, opts);
};

export const getPublicKey = (orgName: string, secretName: string): Promise<PublicKeyResponse> =>
    fetchJson(`/api/v1/org/${orgName}/secret/${secretName}/public`);

export const getSecretAccess = (
    orgName: ConcordKey,
    secretName: ConcordKey
): Promise<GenericOperationResult> => {
    return fetchJson(`/api/v1/org/${orgName}/secret/${secretName}/access`);
};

export const updateSecretAccess = (
    orgName: ConcordKey,
    secretName: ConcordKey,
    entries: ResourceAccessEntry[]
): Promise<GenericOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entries)
    };

    return fetchJson(`/api/v1/org/${orgName}/secret/${secretName}/access/bulk`, opts);
};

export const typeToText = (t: SecretType) => {
    switch (t) {
        case SecretType.KEY_PAIR: {
            return 'Key pair';
        }
        case SecretType.USERNAME_PASSWORD: {
            return 'Username/password';
        }
        case SecretType.DATA: {
            return 'Single value';
        }
        default: {
            throw new Error(`Unexpected value: ${t}`);
        }
    }
};
