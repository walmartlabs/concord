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
import { treeApi, TreeEntry, TreeCategory } from './treeApi';

export const ROOT = 'root';
const SEARCH = 'search';
const PAGE_SIZE = 100;
const VIEWPORT_MAX_AGE = 5000;
interface Chunk {
    fetchedAt: number;
    afterId?: number;
    ids: number[];
    contextIds: number[];
    next: boolean;
    nextId?: number;
}
export interface Branch {
    chunks: Chunk[];
    loading: boolean;
    loadingIndex?: number;
    error?: RequestErrorData;
    failed?: { index: number; afterId?: number };
}
interface Job {
    key: string;
    controller: AbortController;
    run: (signal: AbortSignal) => Promise<void>;
}

/** Only explicitly opened pages are retained. All reads share a four-request scheduler. */
export class TreeLoader {
    readonly entries = new Map<number, TreeEntry>();
    readonly branches = new Map<string, Branch>();
    readonly expanded = new Set<number>();
    readonly matches = new Set<number>();
    private pins = new Set<number>();
    private searchIds = new Set<number>();
    private visible: number[] = [];
    private fetchedAt = new Map<number, number>();
    private pendingNodes = new Set<number>();
    private active = new Map<string, Job>();
    private queue: Job[] = [];
    private disposed = false;
    private selectionGeneration = 0;
    private searchGeneration = 0;
    query = '';
    category?: TreeCategory;
    selectedId?: number;
    error?: RequestErrorData;

    constructor(
        readonly instanceId: ConcordId,
        private changed: () => void,
        private api: Pick<typeof treeApi, 'page' | 'search' | 'nodes' | 'path'> = treeApi,
        private now = Date.now
    ) {}
    get searching() {
        return !!this.query || !!this.category;
    }
    get rootKey() {
        return this.searching ? SEARCH : ROOT;
    }
    get segments() {
        const ids = this.searching
            ? this.searchIds
            : new Set([
                  ...[...this.branches.entries()]
                      .filter(([key]) => key !== SEARCH)
                      .flatMap(([, b]) => b.chunks.flatMap((p) => p.ids)),
                  ...this.pins,
              ]);
        return [...ids]
            .map((id) => this.entries.get(id))
            .filter((e): e is TreeEntry => !!e)
            .sort((a, b) => a.segment.id - b.segment.id);
    }
    branch = (parentId?: number) =>
        this.branches.get(parentId === undefined ? this.rootKey : String(parentId));
    hasMore = (parentId?: number) => {
        const b = this.branch(parentId);
        return !!b?.chunks[b.chunks.length - 1]?.next;
    };
    setVisible = (ids: number[]) => {
        const changed =
            ids.length !== this.visible.length || ids.some((id, i) => id !== this.visible[i]);
        this.visible = ids;
        return changed;
    };
    start = () => {
        this.disposed = false;
        this.load();
    };

    private put(items: TreeEntry[]) {
        const now = this.now();
        items.forEach((e) => {
            this.entries.set(e.segment.id, e);
            this.fetchedAt.set(e.segment.id, now);
        });
    }
    private notify() {
        if (!this.disposed) this.changed();
    }
    private schedule(key: string, run: Job['run']) {
        if (this.disposed || this.active.has(key) || this.queue.some((j) => j.key === key)) return;
        this.queue.push({ key, run, controller: new AbortController() });
        this.pump();
    }
    private pump() {
        while (!this.disposed && this.active.size < 4 && this.queue.length) {
            const job = this.queue.shift()!;
            this.active.set(job.key, job);
            job.run(job.controller.signal)
                .catch((error) => {
                    if (!job.controller.signal.aborted) {
                        this.error = toRequestError(error);
                        this.notify();
                    }
                })
                .finally(() => {
                    if (this.active.get(job.key) === job) this.active.delete(job.key);
                    this.pump();
                });
        }
    }
    private cancel(predicate: (key: string) => boolean) {
        this.queue = this.queue.filter((job) => !predicate(job.key));
        this.active.forEach((job, key) => {
            if (predicate(key)) {
                job.controller.abort();
                this.active.delete(key);
            }
        });
    }

    load = (parentId?: number) => {
        const key = parentId === undefined ? this.rootKey : String(parentId);
        let branch = this.branches.get(key);
        if (!branch) {
            branch = { chunks: [], loading: false };
            this.branches.set(key, branch);
        }
        if (branch.loading) return;
        const last = branch.chunks[branch.chunks.length - 1];
        if (last && !last.next && !branch.error) return;
        this.fetchChunk(key, branch, branch.chunks.length, last?.nextId);
    };
    private fetchChunk(key: string, branch: Branch, index: number, afterId?: number) {
        branch.loading = true;
        branch.loadingIndex = index;
        const previous = branch.chunks[index];
        branch.error = undefined;
        const generation = this.searchGeneration;
        const search = {
            query: this.query || undefined,
            category: this.category,
            afterId,
            limit: PAGE_SIZE,
        };
        this.schedule(`page:${key}:${index}`, async (signal) => {
            try {
                const page =
                    key === SEARCH
                        ? await this.api.search(this.instanceId, search, signal)
                        : await this.api.page(
                              this.instanceId,
                              {
                                  parentId: key === ROOT ? undefined : Number(key),
                                  afterId,
                                  limit: PAGE_SIZE,
                              },
                              signal
                          );
                if (
                    signal.aborted ||
                    this.branches.get(key) !== branch ||
                    (key === SEARCH && generation !== this.searchGeneration)
                )
                    return;
                this.put(page.items);
                this.put(page.ancestors);
                branch.chunks[index] = {
                    fetchedAt: this.now(),
                    afterId,
                    ids: page.items.map((e) => e.segment.id),
                    contextIds: page.ancestors.map((e) => e.segment.id),
                    next: page.next,
                    nextId: page.nextId,
                };
                if (key === SEARCH) {
                    // Search pages retain ancestor context; matching IDs are separate from context nodes.
                    this.matches.clear();
                    this.searchIds.clear();
                    branch.chunks.forEach((chunk) => {
                        chunk.ids.forEach((id) => this.matches.add(id));
                        [...chunk.ids, ...chunk.contextIds].forEach((id) => this.searchIds.add(id));
                    });
                }
                // A changing search result may omit a selected node that used to match.
                // Refresh such nodes separately instead of leaving their metadata stale.
                if (previous) {
                    const returned = new Set(
                        [...page.items, ...page.ancestors].map((e) => e.segment.id)
                    );
                    const visible = new Set([...this.visible, ...this.pins]);
                    this.refreshNodes(
                        [...previous.ids, ...previous.contextIds].filter(
                            (id) => visible.has(id) && !returned.has(id)
                        )
                    );
                }
                branch.failed = undefined;
                this.error = undefined;
            } catch (error) {
                if (!signal.aborted) {
                    branch.error = toRequestError(error);
                    branch.failed = { index, afterId };
                }
            } finally {
                if (this.branches.get(key) === branch) {
                    branch.loading = false;
                    branch.loadingIndex = undefined;
                    this.prune();
                    this.notify();
                }
            }
        });
        this.notify();
    }
    retry = (parentId?: number) => {
        const b = this.branch(parentId);
        if (b?.failed) {
            const { index, afterId } = b.failed;
            b.failed = undefined;
            this.fetchChunk(
                parentId === undefined ? this.rootKey : String(parentId),
                b,
                index,
                afterId
            );
        } else this.load(parentId);
    };
    toggle = (id: number, open: boolean) => {
        if (open) {
            this.expanded.add(id);
            if (!this.branch(id)) this.load(id);
        } else {
            this.expanded.delete(id);
            const descendants = new Set<number>();
            for (const entry of [...this.entries.values()].sort(
                (a, b) => a.segment.id - b.segment.id
            )) {
                const p = entry.segment.parentId;
                if (p === id || (p !== undefined && descendants.has(p)))
                    descendants.add(entry.segment.id);
            }
            // A selected descendant is kept as a path, but hidden along with the collapsed branch.
            [id, ...descendants].forEach((parent) => {
                this.branches.delete(String(parent));
                if (parent !== id) this.expanded.delete(parent);
                this.cancel((key) => key.startsWith(`page:${parent}:`));
            });
            this.prune();
        }
        this.notify();
    };
    collapseAll = () => {
        [...this.expanded].forEach((id) => this.toggle(id, false));
    };
    search = (query: string, category?: TreeCategory) => {
        if (query === this.query && category === this.category) return;
        this.searchGeneration++;
        this.cancel((key) => key.startsWith(`page:${SEARCH}:`));
        this.branches.delete(SEARCH);
        this.matches.clear();
        this.searchIds.clear();
        this.query = query;
        this.category = category;
        this.prune();
        if (this.searching) this.load();
        else this.loadExpandedPages();
        this.notify();
    };
    private cachedPath(id: number): TreeEntry[] | undefined {
        const path: TreeEntry[] = [];
        const seen = new Set<number>();
        let current: number | undefined = id;
        while (current != null) {
            const entry = this.entries.get(current);
            if (!entry || seen.has(current)) return undefined;
            seen.add(current);
            path.push(entry);
            current = entry.segment.parentId;
        }
        return path;
    }
    private loadExpandedPages() {
        if (this.searching) return;
        this.expanded.forEach((id) => {
            if (!this.branch(id)) this.load(id);
        });
    }
    private retainSelection(id: number, path: TreeEntry[]) {
        this.pins = new Set(path.map((e) => e.segment.id));
        path.filter((e) => e.segment.id !== id).forEach((e) => this.expanded.add(e.segment.id));
        if (this.searching) path.forEach((e) => this.searchIds.add(e.segment.id));
        this.error = undefined;
        this.prune();
        this.loadExpandedPages();
    }
    select = (id: number) => {
        this.selectedId = id;
        const generation = ++this.selectionGeneration;
        this.cancel((key) => key === 'path');
        const cached = this.cachedPath(id);
        if (cached) {
            // Parent links are immutable; only metadata needs periodic refresh.
            this.retainSelection(id, cached);
        } else {
            this.schedule('path', async (signal) => {
                const path = await this.api.path(this.instanceId, id, signal);
                if (signal.aborted || generation !== this.selectionGeneration) return;
                this.put(path);
                this.retainSelection(id, path);
                this.notify();
            });
        }
        this.notify();
    };
    private defaultRequested = false;
    selectDefault = (live: boolean) => {
        if (this.defaultRequested || this.selectedId !== undefined) return;
        this.defaultRequested = true;
        const generation = this.selectionGeneration;
        this.schedule('default', async (signal) => {
            let page = await this.api.search(
                this.instanceId,
                { category: 'FAILED', reverse: true, limit: 1 },
                signal
            );
            if (!page.items.length && live && !signal.aborted) {
                page = await this.api.search(
                    this.instanceId,
                    { category: 'RUNNING', reverse: true, limit: 1 },
                    signal
                );
            }
            if (signal.aborted || generation !== this.selectionGeneration) return;
            this.put(page.items);
            this.put(page.ancestors);
            const target =
                page.items[0]?.segment.id ??
                this.segments.find((e) => e.segment.id !== 0)?.segment.id ??
                this.segments[0]?.segment.id;
            if (target !== undefined) this.select(target);
        });
    };

    navigateError = (direction: 1 | -1) => {
        const generation = this.selectionGeneration;
        this.schedule('error', async (signal) => {
            const query = { errorsOnly: true, reverse: direction < 0, limit: 1 };
            let page = await this.api.search(
                this.instanceId,
                { ...query, afterId: this.selectedId },
                signal
            );
            if (!page.items.length && !signal.aborted)
                page = await this.api.search(this.instanceId, query, signal);
            if (signal.aborted || generation !== this.selectionGeneration) return;
            if (page.items.length) {
                this.put(page.items);
                this.put(page.ancestors);
                this.select(page.items[0].segment.id);
            }
        });
    };
    private refreshNodes(ids: number[]) {
        const pending = [...new Set(ids)].filter((id) => !this.pendingNodes.has(id));
        for (let i = 0; i < pending.length; i += PAGE_SIZE) {
            const batch = pending.slice(i, i + PAGE_SIZE);
            batch.forEach((id) => this.pendingNodes.add(id));
            this.schedule(`nodes:${batch.join(',')}`, async (signal) => {
                try {
                    const items = await this.api.nodes(this.instanceId, batch, signal);
                    if (!signal.aborted) {
                        this.put(items);
                        this.prune();
                        this.notify();
                    }
                } finally {
                    batch.forEach((id) => this.pendingNodes.delete(id));
                }
            });
        }
    }
    refresh = () => this.refreshTree(true);
    refreshViewport = () => this.refreshTree(false);
    private refreshTree(force: boolean) {
        this.error = undefined;
        const visible = new Set([...this.visible, ...this.pins]);
        const covered = new Set<number>();
        const cover = (chunk?: Chunk) => {
            if (chunk) [...chunk.ids, ...chunk.contextIds].forEach((id) => covered.add(id));
        };
        const stale = (time?: number) =>
            time === undefined || this.now() - time >= VIEWPORT_MAX_AGE;
        this.branches.forEach((branch, key) => {
            if (this.searching ? key !== SEARCH : key === SEARCH) return;
            if (branch.loading) {
                cover(branch.chunks[branch.loadingIndex!]);
                return;
            }
            if (!branch.chunks.length) {
                this.fetchChunk(key, branch, 0);
                return;
            }
            // Refresh one visible page per branch. Offscreen or collapsed pages make no requests.
            let index = branch.chunks.findIndex((c) => c.ids.some((id) => visible.has(id)));
            if (
                index < 0 &&
                branch.chunks[branch.chunks.length - 1].ids.length === 0 &&
                (key === ROOT || key === SEARCH || visible.has(Number(key)))
            ) {
                // Empty pages must still discover the first segment of a running process/branch.
                index = branch.chunks.length - 1;
            }
            if (index >= 0) {
                const chunk = branch.chunks[index];
                if (force || stale(chunk.fetchedAt)) {
                    cover(chunk);
                    this.fetchChunk(key, branch, index, chunk.afterId);
                }
            }
        });
        // Page responses already carry metadata for their items and ancestor context.
        this.refreshNodes(
            [...visible].filter(
                (id) => !covered.has(id) && (force || stale(this.fetchedAt.get(id)))
            )
        );
    }
    private prune() {
        const retained = new Set(
            [...this.branches.values()].flatMap((b) => b.chunks.flatMap((c) => c.ids))
        );
        this.pins.forEach((id) => retained.add(id));
        this.searchIds.forEach((id) => retained.add(id));
        this.entries.forEach((_, id) => {
            if (!retained.has(id)) {
                this.entries.delete(id);
                this.fetchedAt.delete(id);
            }
        });
    }
    dispose = () => {
        this.disposed = true;
        this.cancel(() => true);
        this.pendingNodes.clear();
        this.branches.forEach((b) => {
            b.loading = false;
        });
    };
}
