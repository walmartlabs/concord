/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import { ConcordId, fetchJson, queryParams } from '../../../api/common';
import { LogSegmentEntry } from '../../../api/process/log';

export type TreeCategory = 'FAILED' | 'RUNNING' | 'WARNINGS' | 'OK';
export interface TreeEntry {
    segment: LogSegmentEntry;
    hasChildren: boolean;
    hasIterations: boolean;
    retried: boolean;
    category: TreeCategory;
}
export interface TreePage {
    items: TreeEntry[];
    ancestors: TreeEntry[];
    next: boolean;
    nextId?: number;
}
export interface TreePageQuery {
    parentId?: number;
    afterId?: number;
    limit?: number;
}
export interface TreeSearchQuery {
    afterId?: number;
    limit?: number;
    query?: string;
    category?: TreeCategory;
    errorsOnly?: boolean;
    reverse?: boolean;
}

async function read<T>(instanceId: ConcordId, suffix: string, signal?: AbortSignal): Promise<T> {
    return fetchJson<T>(`/api/v2/process/${instanceId}/log/tree${suffix}`, { signal });
}
export const resolveStepRun = (id: ConcordId, segmentId: number, signal?: AbortSignal) =>
    read<LogSegmentEntry>(id, `/${segmentId}/run`, signal);

export interface DescendantsQuery {
    beforeId?: number;
    afterId?: number;
    limit?: number;
}

export const treeApi = {
    descendants: (
        id: ConcordId,
        segmentId: number,
        query: DescendantsQuery,
        signal?: AbortSignal
    ) =>
        read<TreePage>(
            id,
            `/${segmentId}/descendants?${queryParams({ limit: 25, ...query })}`,
            signal
        ),
    page: (id: ConcordId, query: TreePageQuery, signal?: AbortSignal) =>
        read<TreePage>(id, `?${queryParams({ limit: 100, ...query })}`, signal),
    search: (id: ConcordId, query: TreeSearchQuery, signal?: AbortSignal) =>
        read<TreePage>(id, `/search?${queryParams({ limit: 100, ...query })}`, signal),
    nodes: (id: ConcordId, ids: number[], signal?: AbortSignal) =>
        read<TreeEntry[]>(id, `/nodes?${ids.map((n) => `segmentId=${n}`).join('&')}`, signal),
    path: (id: ConcordId, segmentId: number, signal?: AbortSignal) =>
        read<TreeEntry[]>(id, `/${segmentId}/path`, signal),
};

export interface SegmentLogPage {
    text: string;
    before: number;
    after: number;
    hasPrevious: boolean;
    hasNext: boolean;
}
export interface SegmentLogPageQuery {
    before?: number;
    after?: number;
    limit: number;
}
export const getSegmentLogPage = (
    id: ConcordId,
    segmentId: number,
    query: SegmentLogPageQuery,
    signal?: AbortSignal
) =>
    fetchJson<SegmentLogPage>(
        `/api/v2/process/${id}/log/segment/${segmentId}/data/page?${queryParams(query)}`,
        { signal }
    );
