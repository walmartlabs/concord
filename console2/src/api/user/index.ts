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

import { ConcordId, ExternalIdentityKey, fetchJson, queryParams } from '../common';

export enum UserType {
    LDAP = 'LDAP',
    LOCAL = 'LOCAL',
    SSO = 'SSO'
}

export interface UserEntry {
    id: ConcordId;
    name: string;
    domain?: string | null;
    type?: UserType | null;
    profileProviderId?: string | null;
    identityProviderIds?: string[] | null;
    displayName?: string;
    email?: string;
}

export type ExternalIdentity = ExternalIdentityKey;

export interface UserIdentity {
    identityId: ConcordId;
    userId?: ConcordId | null;
    identity: ExternalIdentity;
    active: boolean;
}

export interface PaginatedUserEntries {
    items: UserEntry[];
    next: boolean;
}

export const get = async (id: ConcordId): Promise<UserEntry> => fetchJson(`/api/v2/user/${id}`);

export const list = async (
    offset: number,
    limit: number,
    filter?: string
): Promise<PaginatedUserEntries> => {
    const offsetParam = offset > 0 && limit > 0 ? offset * limit : offset;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: UserEntry[] = await fetchJson(
        `/api/v2/user?${queryParams({
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
