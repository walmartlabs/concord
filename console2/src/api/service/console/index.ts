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

import { throttle } from 'lodash';
import { Organizations } from '../../../state/data/orgs';
import { ConcordKey, fetchJson, managedFetch, queryParams } from '../../common';

export interface UserResponse {
    username: string;
    displayName: string;
    orgs: Organizations;
}

export const whoami = async (
    username?: string,
    password?: string,
    rememberMe?: boolean,
    apiKey?: string
): Promise<UserResponse> => {
    const h = new Headers();
    if (apiKey) {
        h.set('Authorization', apiKey);
    } else if (username && password) {
        h.set(
            'Authorization',
            `Basic ${btoa(unescape(encodeURIComponent(username + ':' + password)))}`
        );
    }

    if (rememberMe) {
        h.set('X-Concord-RememberMe', 'true');
    }

    const json = await fetchJson('/api/service/console/whoami', { headers: h });
    return json as UserResponse;
};

export const logout = async () => {
    await managedFetch('/api/service/console/logout', { method: 'POST' });
    return true;
};

// TODO throttle in sagas?
export const isProjectExists = throttle(async (orgName: ConcordKey, name: string): Promise<
    boolean
> => {
    try {
        const json = await fetchJson(`/api/service/console/org/${orgName}/project/${name}/exists`);
        return json as boolean;
    } catch (e) {
        return false;
    }
}, 1000);

// TODO throttle in sagas?
export const isSecretExists = throttle(async (orgName: ConcordKey, name: string): Promise<
    boolean
> => {
    try {
        const json = await fetchJson(`/api/service/console/org/${orgName}/secret/${name}/exists`);
        return json as boolean;
    } catch (e) {
        return false;
    }
}, 1000);

export const isStorageExists = throttle(async (orgName: ConcordKey, name: string): Promise<
    boolean
> => {
    try {
        const json = await fetchJson(
            `/api/service/console/org/${orgName}/jsonstore/${name}/exists`
        );
        return json as boolean;
    } catch (exception) {
        return false;
    }
}, 1000);

export const isStorageQueryExists = throttle(
    async (orgName: ConcordKey, storageName: string, queryName: string): Promise<boolean> => {
        try {
            const json = await fetchJson(
                `/api/service/console/org/${orgName}/jsonstore/${storageName}/query/${queryName}/exists`
            );
            return json as boolean;
        } catch (exception) {
            return false;
        }
    },
    1000
);

export const isRepositoryExists = throttle(
    async (orgName: ConcordKey, projectName: ConcordKey, name: string): Promise<boolean> => {
        try {
            const json = await fetchJson(
                `/api/service/console/org/${orgName}/project/${projectName}/repo/${name}/exists`
            );
            return json as boolean;
        } catch (e) {
            return false;
        }
    },
    1000
);

// TODO throttle in sagas?
export const isTeamExists = throttle(async (orgName: ConcordKey, name: string): Promise<
    boolean
> => {
    try {
        const json = await fetchJson(`/api/service/console/org/${orgName}/team/${name}/exists`);
        return json as boolean;
    } catch (e) {
        return false;
    }
}, 1000);

// TODO throttle in sagas?
export const isApiTokenExists = throttle(async (name: string): Promise<boolean> => {
    try {
        const json = await fetchJson(`/api/service/console/apikey/${name}/exists`);
        return json as boolean;
    } catch (e) {
        return false;
    }
}, 1000);

export interface RepositoryTestRequest {
    orgName: ConcordKey;
    projectName: ConcordKey;
    url: string;
    branch?: string;
    commitId?: string;
    path?: string;
    withSecret?: boolean;
    secretId?: string;
    secretName?: string;
    disabled: boolean;
}

export const testRepository = async (req: RepositoryTestRequest): Promise<void> => {
    const opts: RequestInit = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(req)
    };

    const success = await fetchJson('/api/service/console/repository/test', opts);
    if (!success) {
        throw new Error('Unknown error');
    }
};

export interface UserSearchResult {
    username: string;
    userDomain?: string;
    displayName: string;
}

export const findUsers = (filter: string): Promise<UserSearchResult[]> =>
    fetchJson(`/api/service/console/search/users?${queryParams({ filter })}`);

export interface LdapGroupSearchResult {
    groupName: string;
    displayName: string;
}

export const findLdapGroups = (filter: string): Promise<LdapGroupSearchResult[]> =>
    fetchJson(`/api/service/console/search/ldapGroups?${queryParams({ filter })}`);

export const validatePassword = throttle(
    async (pwd: string): Promise<boolean> => {
        const opts: RequestInit = {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain'
            },
            body: pwd
        };
        const json = await fetchJson('/api/service/console/validate-password', opts);
        return json as boolean;
    }
);
