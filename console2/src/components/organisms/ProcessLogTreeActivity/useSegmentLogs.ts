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

import { useEffect, useMemo, useRef, useState } from 'react';

import { toRequestError } from '../../../api/common';
import type { ConcordId, RequestErrorData } from '../../../api/common';
import { getSegmentLog } from '../../../api/process/log';
import type { LogChunk, LogRange } from '../../../api/process/log';

const CONCURRENCY = 4;
const MAX_CACHE_CHARS = 8 * 1024 * 1024;

export interface SegmentLog {
    text: string;
    // the beginning of the log is not loaded
    truncated: boolean;
    // an explicit full-log request is currently displayed
    full: boolean;
    totalBytes?: number;
    // increases whenever displayed text changes, including equal-length tail replacements
    revision: number;
    loaded: boolean;
    error?: RequestErrorData;
}

export interface SegmentLogRequest {
    id: number;
    // keep loading new data
    live: boolean;
    // how much of the tail to load at first
    tailBytes: number;
    // load the whole log
    full: boolean;
}

interface Entry extends SegmentLog {
    coverageStart?: number;
    // exclusive offset to load through on the next request
    next?: number;
    lastDesiredLive?: boolean;
    finalPending: boolean;
    generation: number;
    lastUsed: number;
}

interface ActiveRequest {
    controller: AbortController;
    generation: number;
    tailBytes: number;
    full: boolean;
    final: boolean;
}

type FetchSegmentLog = (
    instanceId: ConcordId,
    segmentId: number,
    range: LogRange,
    signal?: AbortSignal
) => Promise<LogChunk>;

const EMPTY: SegmentLog = { text: '', truncated: false, full: false, revision: 0, loaded: false };

/**
 * Process-scoped, bounded scheduler and cache for segment log requests.
 */
export class SegmentLogLoader {
    private readonly entries = new Map<number, Entry>();
    private desired = new Map<number, SegmentLogRequest>();
    private queue: number[] = [];
    private readonly queuedIds = new Set<number>();
    private readonly active = new Map<number, ActiveRequest>();
    private disposed = false;
    private clock = 0;

    constructor(
        private readonly instanceId: ConcordId,
        private readonly onChange: () => void,
        private readonly fetchLog: FetchSegmentLog = getSegmentLog
    ) {}

    reconcile = (requests: SegmentLogRequest[]) => {
        if (this.disposed) {
            return;
        }

        const desired = new Map(requests.map((request) => [request.id, request]));
        this.desired = desired;

        this.queue = this.queue.filter((id) => desired.has(id));
        this.queuedIds.clear();
        this.queue.forEach((id) => this.queuedIds.add(id));

        this.active.forEach((request, id) => {
            if (!desired.has(id)) {
                const entry = this.entries.get(id);
                if (entry) {
                    entry.generation++;
                }
                this.active.delete(id);
                request.controller.abort();
            }
        });

        requests.forEach((request) => {
            const entry = this.ensureEntry(request.id);
            if (request.live) {
                entry.lastDesiredLive = true;
                entry.finalPending = false;
            } else if (entry.lastDesiredLive === true) {
                entry.finalPending = true;
            } else if (entry.lastDesiredLive === undefined) {
                entry.lastDesiredLive = false;
            }

            if (this.needsLoad(request.id, entry, request)) {
                this.enqueue(request.id);
            }
        });

        this.trim();
        this.pump();
    };

    poll = () => {
        if (this.disposed) {
            return;
        }
        this.desired.forEach((request) => {
            if (request.live && !this.entries.get(request.id)?.error) {
                this.enqueue(request.id);
            }
        });
        this.pump();
    };

    refresh = () => {
        if (this.disposed) {
            return;
        }
        this.desired.forEach((request) => this.enqueue(request.id));
        this.pump();
    };

    get = (segmentId: number): SegmentLog => {
        const entry = this.entries.get(segmentId);
        if (!entry) {
            return EMPTY;
        }
        entry.lastUsed = ++this.clock;
        return entry;
    };

    retry = (segmentId: number) => {
        if (this.disposed || !this.desired.has(segmentId)) {
            return;
        }
        const entry = this.ensureEntry(segmentId);
        entry.error = undefined;
        this.enqueue(segmentId);
        this.onChange();
        this.pump();
    };

    dispose = () => {
        if (this.disposed) {
            return;
        }
        this.disposed = true;
        this.queue = [];
        this.queuedIds.clear();
        this.active.forEach((request) => request.controller.abort());
        this.active.clear();
        this.desired.clear();
    };

    private ensureEntry(id: number): Entry {
        let entry = this.entries.get(id);
        if (!entry) {
            entry = {
                ...EMPTY,
                finalPending: false,
                generation: 0,
                lastUsed: ++this.clock,
            };
            this.entries.set(id, entry);
        }
        return entry;
    }

    private needsLoad(id: number, entry: Entry, request: SegmentLogRequest): boolean {
        if (entry.error) {
            return false;
        }

        const active = this.active.get(id);
        if (active) {
            if (active.controller.signal.aborted) {
                return true;
            }
            return (
                (entry.finalPending && !active.final) ||
                (request.full && !entry.full && !active.full) ||
                (entry.coverageStart !== undefined &&
                    entry.coverageStart > 0 &&
                    entry.next !== undefined &&
                    entry.next - entry.coverageStart < request.tailBytes &&
                    active.tailBytes < request.tailBytes)
            );
        }

        return (
            !entry.loaded ||
            (request.full && !entry.full) ||
            entry.finalPending ||
            (entry.coverageStart !== undefined &&
                entry.coverageStart > 0 &&
                entry.next !== undefined &&
                entry.next - entry.coverageStart < request.tailBytes)
        );
    }

    private enqueue(id: number) {
        if (!this.desired.has(id) || this.queuedIds.has(id)) {
            return;
        }
        this.queue.push(id);
        this.queuedIds.add(id);
    }

    private pump() {
        while (!this.disposed && this.active.size < CONCURRENCY && this.queue.length > 0) {
            let next: number | undefined;
            const count = this.queue.length;
            for (let i = 0; i < count; i++) {
                const id = this.queue.shift()!;
                this.queuedIds.delete(id);
                if (!this.desired.has(id)) {
                    continue;
                }
                if (this.active.has(id)) {
                    this.queue.push(id);
                    this.queuedIds.add(id);
                    continue;
                }
                next = id;
                break;
            }

            if (next === undefined) {
                return;
            }
            this.dispatch(next);
        }
    }

    private dispatch(id: number) {
        const desired = this.desired.get(id);
        if (!desired) {
            return;
        }

        const entry = this.ensureEntry(id);
        const full = desired.full && !entry.full;
        const final = entry.finalPending && !desired.live;
        let range: LogRange;
        if (full) {
            range = { low: 0 };
        } else if (entry.next === undefined) {
            range = { high: desired.tailBytes };
        } else {
            range = { low: Math.max(0, entry.next - desired.tailBytes) };
        }

        const controller = new AbortController();
        const generation = ++entry.generation;
        const active: ActiveRequest = {
            controller,
            generation,
            tailBytes: desired.tailBytes,
            full,
            final,
        };
        this.active.set(id, active);

        this.fetchLog(this.instanceId, id, range, controller.signal)
            .then((chunk) => this.complete(id, active, chunk))
            .catch((error: unknown) => this.fail(id, active, error))
            .finally(() => {
                if (this.active.get(id) === active) {
                    this.active.delete(id);
                }
                this.trim();
                this.pump();
            });
    }

    private complete(id: number, request: ActiveRequest, chunk: LogChunk) {
        const entry = this.entries.get(id);
        if (
            this.disposed ||
            request.controller.signal.aborted ||
            !entry ||
            entry.generation !== request.generation
        ) {
            return;
        }

        entry.loaded = true;
        entry.error = undefined;
        entry.totalBytes = chunk.range.length;

        const previousText = entry.text;
        if (chunk.data.length > 0) {
            const responseStart = chunk.range.low ?? 0;
            const responseNext = chunk.range.high;
            entry.text = chunk.data;
            entry.coverageStart = responseStart;
            entry.truncated = responseStart > 0;
            if (!request.full && responseStart > 0) {
                entry.full = false;
            }

            // Process log ranges use the returned high value as the next exclusive offset.
            if (responseNext !== undefined && Number.isFinite(responseNext)) {
                entry.next = responseNext;
            }
        }

        if (request.full && chunk.data.length === 0) {
            entry.text = '';
            entry.coverageStart = 0;
            entry.truncated = false;
        }
        if (entry.text !== previousText) {
            entry.revision++;
        }

        if (request.full) {
            entry.full = true;
        }
        if (request.final) {
            entry.finalPending = false;
            entry.lastDesiredLive = false;
        }
        entry.lastUsed = ++this.clock;
        this.onChange();

        const desired = this.desired.get(id);
        if (desired && this.needsLoad(id, entry, desired)) {
            this.enqueue(id);
        }
    }

    private fail(id: number, request: ActiveRequest, error: unknown) {
        const entry = this.entries.get(id);
        if (
            this.disposed ||
            request.controller.signal.aborted ||
            !entry ||
            entry.generation !== request.generation
        ) {
            return;
        }

        entry.loaded = true;
        entry.error = toRequestError(error);
        entry.lastUsed = ++this.clock;
        this.onChange();
    }

    private trim() {
        let size = 0;
        this.entries.forEach((entry) => {
            size += entry.text.length;
        });
        if (size <= MAX_CACHE_CHARS) {
            return;
        }

        const candidates = [...this.entries.entries()]
            .filter(
                ([id]) =>
                    !this.desired.has(id) && !this.queuedIds.has(id) && !this.active.has(id)
            )
            .sort((a, b) => a[1].lastUsed - b[1].lastUsed);
        for (const [id, entry] of candidates) {
            this.entries.delete(id);
            size -= entry.text.length;
            if (size <= MAX_CACHE_CHARS) {
                break;
            }
        }
    }
}

export interface SegmentLogs {
    reconcile: (requests: SegmentLogRequest[]) => void;
    get: (segmentId: number) => SegmentLog;
    retry: (segmentId: number) => void;
}

interface UseSegmentLogsOptions {
    pollInterval: number;
    pollingEnabled: boolean;
    refreshToken: unknown;
}

export const useSegmentLogs = (
    instanceId: ConcordId,
    { pollInterval, pollingEnabled, refreshToken }: UseSegmentLogsOptions
): SegmentLogs => {
    const [, setVersion] = useState(0);
    const loader = useMemo(
        () => new SegmentLogLoader(instanceId, () => setVersion((value) => value + 1)),
        [instanceId]
    );
    const refreshState = useRef({ loader, refreshToken });

    useEffect(() => () => loader.dispose(), [loader]);

    useEffect(() => {
        if (!pollingEnabled || pollInterval <= 0) {
            return;
        }
        const timer = window.setInterval(loader.poll, pollInterval);
        return () => window.clearInterval(timer);
    }, [loader, pollInterval, pollingEnabled]);

    useEffect(() => {
        const previous = refreshState.current;
        if (previous.loader === loader && previous.refreshToken !== refreshToken) {
            loader.refresh();
        }
        refreshState.current = { loader, refreshToken };
    }, [loader, refreshToken]);

    return useMemo(
        () => ({
            reconcile: loader.reconcile,
            get: loader.get,
            retry: loader.retry,
        }),
        [loader]
    );
};
