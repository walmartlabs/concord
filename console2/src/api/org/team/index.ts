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
    OperationResult,
    queryParams,
    RequestError,
    retryRequest
} from '../../common';
import { UserType } from '../../user';

export interface TeamEntry {
    id: ConcordId;
    orgId: ConcordId;
    orgName: ConcordKey;
    name: ConcordKey;
    description?: string;
}

export interface NewTeamEntry {
    name: string;
    description?: string;
}

export interface CreateTeamResponse {
    ok: boolean;
    result: OperationResult;
    id: ConcordId;
}

const shouldRetryTeamRequest = (error: RequestError) => {
    if (!error || (error.status !== 400 && error.status !== 404)) {
        return false;
    }

    return /(?:Organization|Team) not found:/i.test(error.details || '');
};

export const get = (orgName: ConcordKey, teamName: ConcordKey): Promise<TeamEntry> =>
    retryRequest(
        () => fetchJson(`/api/v1/org/${orgName}/team/${teamName}`),
        shouldRetryTeamRequest
    );

export const list = (orgName: ConcordKey): Promise<TeamEntry[]> =>
    retryRequest(() => fetchJson(`/api/v1/org/${orgName}/team`), shouldRetryTeamRequest);

export const createOrUpdate = (
    orgName: ConcordKey,
    entry: NewTeamEntry
): Promise<CreateTeamResponse> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entry)
    };

    return retryRequest(() => fetchJson(`/api/v1/org/${orgName}/team`, opts), shouldRetryTeamRequest);
};

// TODO should we just use createOrUpdate instead?
export const rename = (
    orgName: ConcordKey,
    teamId: ConcordId,
    teamName: ConcordKey
): Promise<CreateTeamResponse> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: teamId,
            name: teamName
        })
    };

    return retryRequest(() => fetchJson(`/api/v1/org/${orgName}/team`, opts), shouldRetryTeamRequest);
};

export const deleteTeam = (
    orgName: ConcordKey,
    teamName: ConcordKey
): Promise<GenericOperationResult> =>
    retryRequest(
        () => fetchJson(`/api/v1/org/${orgName}/team/${teamName}`, { method: 'DELETE' }),
        shouldRetryTeamRequest
    );

export enum TeamRole {
    OWNER = 'OWNER',
    MAINTAINER = 'MAINTAINER',
    MEMBER = 'MEMBER'
}

export interface TeamUserEntry {
    userId: ConcordId;
    username: string;
    userDomain?: string;
    displayName?: string;
    userType?: UserType;
    role: TeamRole;
    memberType: MemberType;
    ldapGroupSource?: string;
}

export interface TeamLdapGroupEntry {
    group: string;
    role: TeamRole;
}

export enum MemberType {
    SINGLE = 'SINGLE',
    LDAP_GROUP = 'LDAP_GROUP'
}

export interface NewTeamUserEntry {
    userId?: ConcordId;
    username?: string;
    userDomain?: string;
    displayName?: string;
    userType?: UserType;
    role: TeamRole;
}

export interface NewTeamLdapGroupEntry {
    group: string;
    role: TeamRole;
}

export const listUsers = (orgName: ConcordKey, teamName: ConcordKey): Promise<TeamUserEntry[]> =>
    retryRequest(
        () => fetchJson(`/api/v1/org/${orgName}/team/${teamName}/users`),
        shouldRetryTeamRequest
    );

export const listLdapGroups = (
    orgName: ConcordKey,
    teamName: ConcordKey
): Promise<TeamLdapGroupEntry[]> =>
    retryRequest(
        () => fetchJson(`/api/v1/org/${orgName}/team/${teamName}/ldapGroups`),
        shouldRetryTeamRequest
    );

export const addUsers = (
    orgName: ConcordKey,
    teamName: ConcordKey,
    replace: boolean,
    users: NewTeamUserEntry[]
): Promise<{}> => {
    const opts = {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(users)
    };

    return retryRequest(
        () =>
            fetchJson(
                `/api/v1/org/${orgName}/team/${teamName}/users?${queryParams({ replace })}`,
                opts
            ),
        shouldRetryTeamRequest
    );
};

export const addLdapGroups = (
    orgName: ConcordKey,
    teamName: ConcordKey,
    replace: boolean,
    groups: NewTeamLdapGroupEntry[]
): Promise<{}> => {
    const opts = {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(groups)
    };

    return retryRequest(
        () =>
            fetchJson(
                `/api/v1/org/${orgName}/team/${teamName}/ldapGroups?${queryParams({ replace })}`,
                opts
            ),
        shouldRetryTeamRequest
    );
};
