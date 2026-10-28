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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { isTransientRequestError, toRequestError, ConcordId } from '../../../api/common';
import { PollingError, usePolling } from '../../../api/usePolling';
import { getSegmentLogPage, SegmentLogPage, SegmentLogPageQuery } from './logApi';

const ignoreLoading = () => {};
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
    error?: PollingError;
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
    private cacheChars = 0;
    private idleWaiters = new Set<() => void>();

    constructor(
        private readonly instanceId: ConcordId,
        private readonly changed: () => void,
        private readonly fetchPage: FetchPage = getSegmentLogPage,
        private readonly requested: () => void = ignoreLoading
    ) {}

    reconcile = (requests: SegmentLogRequest[]) => {
        if (this.disposed) return;
        const selectionChanged =
            requests.length !== this.desired.size ||
            requests.some((r) => {
                const previous = this.desired.get(r.id);
                return !previous || previous.live !== r.live || previous.tailBytes !== r.tailBytes;
            });
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
        if (selectionChanged) this.requested();
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
        this.requested();
    };
    retry = (id: number) => {
        if (!this.desired.has(id) || this.disposed) return;
        this.entry(id).error = undefined;
        this.queue.add(id);
        this.changed();
        this.pump();
        this.requested();
    };
    poll = (live = true) => {
        this.desired.forEach((r) => {
            const entry = this.entry(r.id);
            if (entry.error && !entry.error.retrying) return;
            if (
                ((live && r.live) || entry.error?.retrying || entry.finalPending) &&
                !this.active.has(r.id)
            ) {
                this.queue.add(r.id);
            }
        });
        this.pump();
    };
    waitForIdle = (): Promise<void> => {
        if (this.disposed || (this.active.size === 0 && this.queue.size === 0)) {
            return Promise.resolve();
        }
        return new Promise((resolve) => this.idleWaiters.add(resolve));
    };
    getTransientError = (): PollingError | undefined => {
        for (const id of this.desired.keys()) {
            const error = this.entry(id).error;
            if (error?.retrying) return error;
        }
        return undefined;
    };
    hasLiveRequests = () =>
        [...this.desired.values()].some(
            (r) => r.live && (!this.entry(r.id).error || this.entry(r.id).error?.retrying)
        );
    refresh = () => {
        this.desired.forEach((r) => {
            if (!this.active.has(r.id)) this.queue.add(r.id);
        });
        this.pump();
        this.requested();
    };
    dispose = () => {
        this.disposed = true;
        this.active.forEach((a) => a.controller.abort());
        this.active.clear();
        this.queue.clear();
        this.resolveIdle();
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
        if (this.active.size === 0 && this.queue.size === 0) this.resolveIdle();
    }
    private resolveIdle() {
        this.idleWaiters.forEach((resolve) => resolve());
        this.idleWaiters.clear();
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
                const previousLength = entry.text.length;
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
                this.cacheChars += entry.text.length - previousLength;
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
                    entry.error = {
                        ...toRequestError(error),
                        retrying: isTransientRequestError(error),
                        loaded: entry.loaded,
                    };
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
        if (this.cacheChars <= MAX_CACHE_CHARS) return;
        const candidates = [...this.entries]
            .filter(([id]) => !this.desired.has(id) && !this.active.has(id))
            .sort((a, b) => a[1].lastUsed - b[1].lastUsed);
        for (const [id, entry] of candidates) {
            if (this.cacheChars <= MAX_CACHE_CHARS) break;
            this.entries.delete(id);
            this.cacheChars -= entry.text.length;
        }
    }
}

export interface SegmentLogs {
    pollingError?: PollingError;
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
    const [workVersion, setWorkVersion] = useState(0);
    const loader = useMemo(
        () =>
            new SegmentLogLoader(
                instanceId,
                () => setVersion((value) => value + 1),
                getSegmentLogPage,
                () => setWorkVersion((value) => value + 1)
            ),
        [instanceId]
    );
    const refreshState = useRef({ loader, refreshToken });

    useEffect(() => () => loader.dispose(), [loader]);

    const request = useCallback(async () => {
        const live = pollingEnabled && pollInterval > 0;
        loader.poll(live);
        await loader.waitForIdle();
        const error = loader.getTransientError();
        if (error) throw error;
        return live && loader.hasLiveRequests();
    }, [loader, pollingEnabled, pollInterval, workVersion]);
    const pollingError = usePolling(request, pollInterval, ignoreLoading, false, {
        reportInitialErrors: true,
    });

    useEffect(() => {
        const previous = refreshState.current;
        if (previous.loader === loader && previous.refreshToken !== refreshToken) {
            loader.refresh();
        }
        refreshState.current = { loader, refreshToken };
    }, [loader, refreshToken]);

    return useMemo(
        () => ({
            pollingError,
            reconcile: loader.reconcile,
            get: loader.get,
            retry: loader.retry,
            previous: loader.previous,
        }),
        [loader, pollingError]
    );
};
