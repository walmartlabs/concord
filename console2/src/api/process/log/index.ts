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
    fetchJson,
    managedFetch,
    queryParams,
    readBody,
    responseError,
} from '../../common';

export interface LogRange {
    unit?: string;
    length?: number;
    low?: number;
    high?: number;
}

export interface LogChunk {
    data: string;
    range: LogRange;
}

const str = (s?: {}): string => (s === undefined ? '' : String(s));

const formatRangeHeader = (range: LogRange) => ({
    Range: `bytes=${str(range.low)}-${str(range.high)}`,
});

const parseRange = (response: Response): LogRange => {
    const header = response.headers.get('Content-Range');
    if (!header) {
        throw responseError(response, 'Content-Range header is missing');
    }
    const regex = /^bytes (\d*)-(\d*)\/(\d*)$/;
    const m = regex.exec(header);
    if (!m) {
        throw responseError(response, 'Invalid Content-Range header', header);
    }

    return {
        unit: 'bytes',
        length: parseInt(m[3], 10),
        low: parseInt(m[1], 10),
        high: parseInt(m[2], 10),
    };
};

const toChunk = (data: string, range: LogRange): LogChunk => {
    // we assume that the data is aligned by \n
    // this will work only with our current implementation of the API
    return {
        data,
        range,
    };
};

export const getLog = async (instanceId: ConcordId, range: LogRange): Promise<LogChunk> => {
    const opts = {
        headers: formatRangeHeader(range),
    };

    const resp = await managedFetch(`/api/v1/process/${instanceId}/log`, opts);

    const parsedRange = parseRange(resp);
    const data = await readBody(resp, (r) => r.text());
    return toChunk(data, parsedRange);
};

export enum SegmentStatus {
    OK = 'OK',
    FAILED = 'FAILED',
    RUNNING = 'RUNNING',
    SUSPENDED = 'SUSPENDED',
}

export enum SegmentKind {
    // the whole execution of a step
    STEP = 'STEP',
    // one iteration of a loop
    ITERATION = 'ITERATION',
    // one attempt of a step with "retry", including the first one
    ATTEMPT = 'ATTEMPT',
    // the "error" block of the parent step
    ERROR = 'ERROR',
    // the whole execution of a "parallel" block
    PARALLEL = 'PARALLEL',
}

export interface LogSegmentEntry {
    id: number;
    // the segment open in the runtime thread when the segment started
    parentId?: number;
    // undefined for segments created by older runners
    kind?: SegmentKind;
    // loop iteration index, starting from 0
    iterationIndex?: number;
    // retry attempt index, starting from 0
    attemptIndex?: number;
    // runtime thread, undefined for the main thread
    threadId?: number;
    correlationId?: string;
    name: string;
    createdAt: string;
    status?: SegmentStatus;
    statusUpdatedAt?: string;
    warnings?: number;
    errors?: number;
}

export interface PaginatedLogSegmentEntry {
    items: LogSegmentEntry[];
    next: boolean;
}

export const listLogSegments = async (
    instanceId: ConcordId,
    offset: number,
    limit: number,
    signal?: AbortSignal
): Promise<PaginatedLogSegmentEntry> => {
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: LogSegmentEntry[] = await fetchJson(
        `/api/v2/process/${instanceId}/log/segment?${queryParams({
            offset,
            limit: limitParam,
        })}`,
        { signal }
    );

    const hasMoreElements: boolean = limit > 0 && data.length > limit;

    if (limit > 0 && hasMoreElements) {
        data.pop();
    }

    return {
        items: data,
        next: hasMoreElements,
    };
};

export const getSegmentLog = async (
    instanceId: ConcordId,
    segmentId: number,
    range: LogRange,
    signal?: AbortSignal
): Promise<LogChunk> => {
    const opts: RequestInit = {
        headers: formatRangeHeader(range),
        signal,
    };

    const resp = await managedFetch(
        `/api/v2/process/${instanceId}/log/segment/${segmentId}/data`,
        opts
    );

    const parsedRange = parseRange(resp);
    const data = await readBody(resp, (r) => r.text());
    return toChunk(data, parsedRange);
};

export class LogTooLargeError extends Error {
    constructor(public readonly sizeBytes: number, public readonly limitBytes: number) {
        super(`Log is too large (${sizeBytes} bytes, limit is ${limitBytes} bytes)`);
        this.name = 'LogTooLargeError';
    }
}

export const getFullSegmentLog = async (
    instanceId: ConcordId,
    segmentId: number,
    maxBytes?: number
): Promise<string> => {
    if (maxBytes === undefined) {
        const response = await managedFetch(
            `/api/v2/process/${instanceId}/log/segment/${segmentId}/data`
        );
        return response.text();
    }

    // ask the server for at most maxBytes: it caps the query on its side (rather than
    // loading the entire, potentially huge, segment into memory before we get a say),
    // and the Content-Range total tells us the real size regardless of compression/chunking
    const chunk = await getSegmentLog(instanceId, segmentId, { low: 0, high: maxBytes });
    const total = chunk.range.length;
    if (total !== undefined && total > maxBytes) {
        throw new LogTooLargeError(total, maxBytes);
    }

    return chunk.data;
};
