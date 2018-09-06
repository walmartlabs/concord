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

import { fetchJson, ConcordKey, ConcordId } from '../common';

export enum OrganizationVisibility {
    PUBLIC = 'PUBLIC',
    PRIVATE = 'PRIVATE'
}

export interface ColumnDefinition {
    caption: string;
    source: string;
    render?: 'process-link' | 'project-link' | 'timestamp';
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

export const list = (onlyCurrent: boolean): Promise<OrganizationEntry[]> =>
    fetchJson(`/api/v1/org?onlyCurrent=${onlyCurrent}`);

export const get = (orgName: ConcordKey): Promise<OrganizationEntry> =>
    fetchJson<OrganizationEntry>(`/api/v1/org/${orgName}`);
