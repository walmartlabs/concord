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

import { ConcordId, ConcordKey, fetchJson, queryParams } from '../../common';
import { ProcessEntry, ProcessStatus } from '../../process';

export interface SearchFilter {
    parentInstanceId?: ConcordId;
    projectId?: ConcordId;
    beforeCreatedAt?: string;
    tags?: string[];
    status?: ProcessStatus;
    initiator?: string;
    limit?: number;
    offset?: number;
}

export interface PaginatedProcessEntries {
    items: ProcessEntry[];
    next?: number;
    prev?: number;
}

export const list = (
    orgName?: ConcordKey,
    projectName?: ConcordKey,
    filters?: SearchFilter
): Promise<PaginatedProcessEntries> => {
    const limit = filters && filters.limit ? filters.limit : 50;
    if (filters && filters.limit) {
        filters.limit = parseInt(filters.limit.toString(), 10) + 1;
    }

    let baseUri = '/api/v1';

    if (orgName) {
        baseUri = `/api/v1/org/${orgName}`;
        if (projectName) {
            baseUri += `/project/${projectName}`;
        }
    }

    const qp = filters ? '?' + queryParams(filters) : '';

    return fetchJson(`${baseUri}/process${qp}`).then((processEntries: ProcessEntry[]) => {
        const hasMoreElements = limit && processEntries.length > limit;
        const offset: number = filters && filters.offset ? filters.offset : 0;

        if (hasMoreElements) {
            processEntries.pop();
        }

        const nextOffset = offset + parseInt(limit.toString(), 10);
        const prevOffset = offset - limit;
        const onFirstPage = offset === 0;

        const nextPage = !!hasMoreElements ? nextOffset : undefined;
        const prevPage = !onFirstPage ? prevOffset : undefined;

        return {
            items: processEntries,
            next: nextPage,
            prev: prevPage
        };
    });
};

export interface StartProcessResponse {
    ok: boolean;
    instanceId: string;
}

export interface RestoreProcessResponse {
    ok: boolean;
}
