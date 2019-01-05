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

import { ConcordKey, fetchJson, queryParams } from '../../common';
import { ProcessEntry } from '../../process';
import { ColumnDefinition } from '../index';

export interface ColumnFilter {
    column: ColumnDefinition;
    filter: string;
}

export interface ProcessFilters {
    [source: string]: string;
}

export interface PaginationFilters {
    limit?: number;
    offset?: number;
}

export interface PaginatedProcessEntries {
    items: ProcessEntry[];
    next?: number;
    prev?: number;
}

function combine(filters?: any, pagination?: any) {
    if (filters === undefined && pagination === undefined) {
        return undefined;
    }

    type Result = { [name: string]: any };
    const result: Result = {};

    if (filters !== undefined) {
        Object.keys(filters)
            .filter((k) => k !== undefined)
            .forEach((key) => (result[key] = filters[key]));
    }
    if (pagination !== undefined) {
        Object.keys(pagination)
            .filter((k) => k !== undefined)
            .forEach((key) => (result[key] = pagination[key]));
    }

    return result;
}

export const list = (
    orgName?: ConcordKey,
    projectName?: ConcordKey,
    filters?: ProcessFilters,
    pagination?: PaginationFilters
): Promise<PaginatedProcessEntries> => {
    const limit = pagination && pagination.limit ? pagination.limit : 50;
    const requestLimit = parseInt(limit.toString(), 10) + 1;

    const filterParams = combine(
        { ...filters, org: orgName, project: projectName },
        { ...pagination, limit: requestLimit }
    );

    const qp = filterParams ? '?' + queryParams(filterParams) : '';

    const data: Promise<ProcessEntry[]> = fetchJson(`/api/v1/process${qp}`);
    return data.then((processEntries: ProcessEntry[]) => {
        const hasMoreElements = limit && processEntries.length > limit;
        const offset: number = pagination && pagination.offset ? pagination.offset : 0;

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
