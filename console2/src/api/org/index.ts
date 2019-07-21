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

import { fetchJson, ConcordKey, ConcordId, OperationResult, EntityOwner, Owner } from '../common';

export enum OrganizationVisibility {
    PUBLIC = 'PUBLIC',
    PRIVATE = 'PRIVATE'
}

export type SearchType = 'substring' | 'equals';

export type SearchValueType = 'string';

export interface SearchOption {
    value: string;
    text: string;
}

export interface ColumnDefinition {
    builtin?: string;
    caption: string;
    source: string;
    textAlign?: 'center' | 'left' | 'right';
    collapsing?: boolean;
    render?:
        | 'process-link'
        | 'process-status'
        | 'project-link'
        | 'timestamp'
        | 'repo-link'
        | 'string-array';
    searchValueType?: SearchValueType;
    searchType?: SearchType;
    searchOptions?: SearchOption[];
}

export interface OrganizationEntryMetaUI {
    processList?: ColumnDefinition[];
}

export interface OrganizationEntryMeta {
    ui?: OrganizationEntryMetaUI;
}

export interface OrganizationEntry {
    id: string;
    name: string;
    owner?: EntityOwner;
    visibility: OrganizationVisibility;
    meta?: OrganizationEntryMeta;
}

export enum ResourceAccessLevel {
    OWNER = 'OWNER',
    WRITER = 'WRITER',
    READER = 'READER'
}

export interface ResourceAccessEntry {
    teamId: ConcordId;
    teamName: ConcordKey;
    level: ResourceAccessLevel;
}

export interface OrganizationOperationResult {
    ok: boolean;
    id: ConcordId;
    result: OperationResult;
}

export const list = (onlyCurrent: boolean): Promise<OrganizationEntry[]> =>
    fetchJson(`/api/v1/org?onlyCurrent=${onlyCurrent}`);

export const get = (orgName: ConcordKey): Promise<OrganizationEntry> =>
    fetchJson<OrganizationEntry>(`/api/v1/org/${orgName}`);

export const changeOwner = (
    orgId: ConcordId,
    owner: Owner
): Promise<OrganizationOperationResult> => {
    const opts = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: orgId,
            owner: { username: owner.username, userDomain: owner.userDomain }
        })
    };

    return fetchJson(`/api/v1/org`, opts);
};
