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

import { ConcordId, RequestErrorData, toRequestError } from '../../../api/common';
import { treeApi, TreeEntry, TreePage } from './treeApi';

export const LOG_SEGMENTS_PAGE = 100;

/** Owns the right pane's selection. Tree expansion never changes this window. */
export class LogSubtreeLoader {
    readonly entries = new Map<number, TreeEntry>();
    readonly included = new Set<number>();
    loading = false;
    initialized = false;
    hasPrevious = false;
    error?: RequestErrorData;
    private oldest?: number;
    private newest: number;
    private controller = new AbortController();
    private pendingRefresh = false;
    private failed: 'initial' | 'previous' | 'refresh' = 'initial';

    constructor(
        private readonly instanceId: ConcordId,
        readonly rootId: number,
        private readonly changed: () => void,
        private readonly api: Pick<typeof treeApi, 'descendants' | 'nodes'> = treeApi
    ) {
        this.included.add(rootId);
        this.newest = rootId;
    }

    load = () => this.perform('initial');
    previous = () => this.perform('previous');
    retry = () => this.perform(this.failed);
    refresh = () => {
        if (this.loading) {
            this.pendingRefresh = true;
            return;
        }
        return this.perform(this.initialized ? 'refresh' : 'initial');
    };
    dispose = () => this.controller.abort();

    private merge(page: TreePage) {
        [...page.ancestors, ...page.items].forEach((entry) =>
            this.entries.set(entry.segment.id, entry)
        );
        page.items.forEach(({ segment }) => {
            this.included.add(segment.id);
            this.newest = Math.max(this.newest, segment.id);
        });
    }

    private async perform(mode: 'initial' | 'previous' | 'refresh') {
        if (
            this.loading ||
            this.controller.signal.aborted ||
            (mode === 'previous' && !this.hasPrevious)
        )
            return;
        this.loading = true;
        this.error = undefined;
        this.changed();
        const signal = this.controller.signal;
        try {
            if (mode === 'refresh') {
                // Refresh only this pane's metadata, in bounded batches.
                const ids = [...this.entries.keys()];
                for (let i = 0; i < ids.length; i += 100) {
                    const updated = await this.api.nodes(
                        this.instanceId,
                        ids.slice(i, i + 100),
                        signal
                    );
                    if (signal.aborted) return;
                    updated.forEach((entry) => this.entries.set(entry.segment.id, entry));
                }
                // Ascending cursor avoids skipping a burst larger than one page.
                let more = true;
                while (more) {
                    const page = await this.api.descendants(
                        this.instanceId,
                        this.rootId,
                        { afterId: this.newest, limit: LOG_SEGMENTS_PAGE },
                        signal
                    );
                    if (signal.aborted) return;
                    this.merge(page);
                    more = page.next && page.items.length > 0;
                    this.changed();
                }
            } else {
                const page = await this.api.descendants(
                    this.instanceId,
                    this.rootId,
                    {
                        beforeId: mode === 'previous' ? this.oldest : undefined,
                        limit: LOG_SEGMENTS_PAGE,
                    },
                    signal
                );
                if (signal.aborted) return;
                this.merge(page);
                this.oldest = page.nextId;
                this.hasPrevious = page.next;
                this.initialized = true;
            }
        } catch (error) {
            if (!signal.aborted) {
                this.error = toRequestError(error);
                this.failed = mode;
            }
        } finally {
            if (!signal.aborted) {
                this.loading = false;
                this.changed();
                if (this.pendingRefresh && !this.error) {
                    this.pendingRefresh = false;
                    void this.refresh();
                }
            }
        }
    }
}
