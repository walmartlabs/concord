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
import { toRequestError, ConcordId, RequestErrorData } from '../../../api/common';
import { getSegmentLogPage, SegmentLogPage, SegmentLogPageQuery } from './logApi';

const CONCURRENCY = 4;
const MAX_CACHE_CHARS = 8 * 1024 * 1024;

export interface SegmentLog {
    text: string;
    truncated: boolean;
    before: number;
    after: number;
    revision: number;
    loaded: boolean;
    loadingPrevious: boolean;
    error?: RequestErrorData;
}
export interface SegmentLogRequest {
    id: number;
    live: boolean;
    tailBytes: number;
}
interface Entry extends SegmentLog {
    finalPending: boolean;
    lastDesiredLive?: boolean;
    initialBytes: number;
    lastUsed: number;
}
interface ActiveRequest {
    controller: AbortController;
    mode: 'initial' | 'previous' | 'append';
    final: boolean;
}
type FetchPage = (
    id: ConcordId,
    segmentId: number,
    query: SegmentLogPageQuery,
    signal?: AbortSignal
) => Promise<SegmentLogPage>;
const EMPTY: SegmentLog = {
    text: '',
    truncated: false,
    before: 0,
    after: 0,
    revision: 0,
    loaded: false,
    loadingPrevious: false,
};

/** One scheduler per process. Log pages are disjoint; the server owns block boundaries. */
export class SegmentLogLoader {
    private entries = new Map<number, Entry>();
    private desired = new Map<number, SegmentLogRequest>();
    private queue = new Set<number>();
    private active = new Map<number, ActiveRequest>();
    private disposed = false;
    private clock = 0;

    constructor(
        private readonly instanceId: ConcordId,
        private readonly changed: () => void,
        private readonly fetchPage: FetchPage = getSegmentLogPage
    ) {}

    reconcile = (requests: SegmentLogRequest[]) => {
        if (this.disposed) return;
        this.desired = new Map(requests.map((r) => [r.id, r]));
        this.queue.forEach((id) => {
            if (!this.desired.has(id)) this.queue.delete(id);
        });
        this.active.forEach((active, id) => {
            if (!this.desired.has(id)) {
                this.active.delete(id);
                active.controller.abort();
            }
        });
        requests.forEach((request) => {
            const entry = this.entry(request.id);
            if (request.live) {
                entry.lastDesiredLive = true;
                entry.finalPending = false;
            } else if (entry.lastDesiredLive) entry.finalPending = true;
            if (entry.error) return;
            if (entry.loaded && request.tailBytes > entry.initialBytes && entry.truncated) {
                entry.loadingPrevious = true;
                entry.initialBytes = request.tailBytes;
            }
            if (!entry.loaded || entry.loadingPrevious || entry.finalPending) {
                const active = this.active.get(request.id);
                if (
                    !active ||
                    (entry.finalPending && !active.final) ||
                    (entry.loadingPrevious && active.mode !== 'previous')
                )
                    this.queue.add(request.id);
            }
        });
        this.pump();
    };
    get = (id: number): SegmentLog => {
        const entry = this.entries.get(id);
        if (!entry) return EMPTY;
        entry.lastUsed = ++this.clock;
        return entry;
    };
    previous = (id: number) => {
        if (!this.desired.has(id) || this.disposed) return;
        const entry = this.entry(id);
        if (!entry.truncated || entry.loadingPrevious) return;
        entry.loadingPrevious = true;
        entry.error = undefined;
        this.queue.add(id);
        this.changed();
        this.pump();
    };
    retry = (id: number) => {
        if (!this.desired.has(id) || this.disposed) return;
        this.entry(id).error = undefined;
        this.queue.add(id);
        this.changed();
        this.pump();
    };
    poll = () => {
        this.desired.forEach((r) => {
            if (r.live && !this.entry(r.id).error && !this.active.has(r.id)) this.queue.add(r.id);
        });
        this.pump();
    };
    refresh = () => {
        this.desired.forEach((r) => {
            if (!this.active.has(r.id)) this.queue.add(r.id);
        });
        this.pump();
    };
    dispose = () => {
        this.disposed = true;
        this.active.forEach((a) => a.controller.abort());
        this.active.clear();
        this.queue.clear();
    };
    private entry(id: number): Entry {
        let entry = this.entries.get(id);
        if (!entry) {
            entry = {
                ...EMPTY,
                finalPending: false,
                initialBytes: 0,
                lastUsed: ++this.clock,
            };
            this.entries.set(id, entry);
        }
        return entry;
    }
    private pump() {
        if (this.disposed) return;
        for (const id of this.queue) {
            if (this.active.size >= CONCURRENCY) break;
            if (this.active.has(id)) continue;
            this.queue.delete(id);
            const desired = this.desired.get(id);
            if (desired) this.dispatch(desired);
        }
        this.trim();
    }
    private dispatch(desired: SegmentLogRequest) {
        const id = desired.id;
        const entry = this.entry(id);
        const mode = !entry.loaded ? 'initial' : entry.loadingPrevious ? 'previous' : 'append';
        const query: SegmentLogPageQuery = { limit: desired.tailBytes };
        if (mode === 'previous') query.before = entry.before;
        if (mode === 'append') query.after = entry.after;
        const active: ActiveRequest = {
            controller: new AbortController(),
            mode,
            final: entry.finalPending && mode !== 'previous',
        };
        this.active.set(id, active);
        this.fetchPage(this.instanceId, id, query, active.controller.signal)
            .then((page) => {
                if (active.controller.signal.aborted || this.disposed) return;
                if (mode === 'initial') {
                    entry.text = page.text;
                    entry.before = page.before;
                    entry.after = page.after;
                    entry.truncated = page.hasPrevious;
                    entry.initialBytes = desired.tailBytes;
                } else if (mode === 'previous') {
                    entry.text = page.text + entry.text;
                    entry.before = page.before;
                    entry.truncated = page.hasPrevious;
                    entry.loadingPrevious = false;
                } else {
                    entry.text += page.text;
                    entry.after = page.after;
                }
                entry.loaded = true;
                entry.error = undefined;
                if (page.text) entry.revision++;
                if (active.final && !page.hasNext) {
                    entry.finalPending = false;
                    entry.lastDesiredLive = false;
                }
                // Drain a burst in bounded pages. Never loop on an empty response.
                if (page.text && page.hasNext && mode !== 'previous') this.queue.add(id);
                if (entry.finalPending && !active.final) this.queue.add(id);
                this.changed();
            })
            .catch((error) => {
                if (!active.controller.signal.aborted && !this.disposed) {
                    entry.error = toRequestError(error);
                    this.queue.delete(id);
                    this.changed();
                }
            })
            .finally(() => {
                if (this.active.get(id) === active) this.active.delete(id);
                this.pump();
            });
    }
    private trim() {
        let size = [...this.entries.values()].reduce((sum, e) => sum + e.text.length, 0);
        const candidates = [...this.entries]
            .filter(([id]) => !this.desired.has(id) && !this.active.has(id))
            .sort((a, b) => a[1].lastUsed - b[1].lastUsed);
        for (const [id, entry] of candidates) {
            if (size <= MAX_CACHE_CHARS) break;
            this.entries.delete(id);
            size -= entry.text.length;
        }
    }
}

export interface SegmentLogs {
    reconcile: (requests: SegmentLogRequest[]) => void;
    get: (segmentId: number) => SegmentLog;
    retry: (segmentId: number) => void;
    previous: (segmentId: number) => void;
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
        () =>
            new SegmentLogLoader(
                instanceId,
                () => setVersion((value) => value + 1),
                getSegmentLogPage
            ),
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
            previous: loader.previous,
        }),
        [loader]
    );
};
