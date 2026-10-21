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

import { expect, test, vi } from 'vitest';
import { TreeLoader } from '../TreeLoader';
import { TreeEntry, TreePage, treeApi } from '../treeApi';

const entry = (id: number, parentId?: number, hasChildren = false): TreeEntry => ({
    segment: { id, parentId, name: `step ${id}`, createdAt: '2026-10-19T00:00:00Z' },
    hasChildren,
    hasIterations: false,
    retried: false,
    category: 'OK',
});
const page = (items: TreeEntry[], next = false, ancestors: TreeEntry[] = []): TreePage => ({
    items,
    ancestors,
    next,
    nextId: items[items.length - 1]?.segment.id,
});
const tick = async () => {
    for (let i = 0; i < 12; i++) await Promise.resolve();
};
const api = () => ({
    page: vi.fn<typeof treeApi.page>(),
    search: vi.fn<typeof treeApi.search>(),
    nodes: vi.fn<typeof treeApi.nodes>(),
    path: vi.fn<typeof treeApi.path>(),
});

test('opening a large loop loads only one page, then pages by ID; collapse drops descendants', async () => {
    const calls = api();
    calls.page
        .mockResolvedValueOnce(page([entry(1, undefined, true)]))
        .mockResolvedValueOnce(
            page(
                Array.from({ length: 100 }, (_, n) => entry(n + 2, 1)),
                true
            )
        )
        .mockResolvedValueOnce(page([entry(102, 1)], false));
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.start();
    await tick();
    expect(loader.segments.map((e) => e.segment.id)).toEqual([1]);
    expect(calls.page).toHaveBeenCalledTimes(1);
    loader.toggle(1, true);
    await tick();
    expect(loader.segments).toHaveLength(101);
    expect(calls.page).toHaveBeenCalledTimes(2);
    loader.load(1);
    await tick();
    expect(calls.page.mock.calls[2][1]).toMatchObject({ parentId: 1, afterId: 101, limit: 100 });
    expect(loader.segments).toHaveLength(102);
    loader.toggle(1, false);
    expect(loader.segments.map((e) => e.segment.id)).toEqual([1]);
    expect(loader.entries.size).toBe(1);
    expect(loader.branches.has('1')).toBe(false);
});

test('a late response for a collapsed branch cannot restore its children', async () => {
    const calls = api();
    let resolve!: (p: TreePage) => void;
    calls.page.mockResolvedValueOnce(page([entry(1, undefined, true)])).mockImplementationOnce(
        () =>
            new Promise((r) => {
                resolve = r;
            })
    );
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.start();
    await tick();
    loader.toggle(1, true);
    const signal = calls.page.mock.calls[1][2]!;
    loader.toggle(1, false);
    expect(signal.aborted).toBe(true);
    resolve(page([entry(2, 1)]));
    await tick();
    expect(loader.entries.has(2)).toBe(false);
});

test('deep links load expanded child pages; global search includes unloaded matches', async () => {
    const calls = api();
    calls.path.mockResolvedValue([entry(1, undefined, true), entry(50000, 1)]);
    calls.page.mockResolvedValueOnce(page([entry(50000, 1)]));
    calls.search.mockResolvedValue(page([entry(99999, 1)], false, [entry(1, undefined, true)]));
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.select(50000);
    await tick();
    expect(calls.page).toHaveBeenCalledTimes(1);
    expect(calls.page.mock.calls[0][1]).toMatchObject({ parentId: 1, limit: 100 });
    expect(loader.hasMore(1)).toBe(false);
    expect(loader.expanded.has(1)).toBe(true);
    expect(loader.segments.map((e) => e.segment.id)).toEqual([1, 50000]);
    loader.search('needle', 'FAILED');
    await tick();
    expect(calls.search.mock.calls[0][1]).toMatchObject({
        query: 'needle',
        category: 'FAILED',
        limit: 100,
    });
    expect(loader.segments.map((e) => e.segment.id)).toEqual([1, 99999]);
    expect([...loader.matches]).toEqual([99999]);
});

test('refresh bounds requests to visible pages and IDs and discovers children appended to the final page', async () => {
    const calls = api();
    calls.page
        .mockResolvedValueOnce(page([entry(1, undefined, true)]))
        .mockResolvedValueOnce(page([entry(2, 1)]));
    calls.nodes.mockResolvedValue([entry(2, 1)]);
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.start();
    await tick();
    loader.toggle(1, true);
    await tick();
    calls.page.mockResolvedValue(page([entry(2, 1), entry(3, 1)]));
    loader.setVisible([2]);
    loader.refresh();
    await tick();
    expect(calls.nodes).not.toHaveBeenCalled();
    expect(calls.page.mock.calls[2][1]).toMatchObject({ parentId: 1, limit: 100 });
    expect(loader.entries.has(3)).toBe(true);
    loader.toggle(1, false);
    loader.setVisible([1]);
    calls.page.mockClear();
    calls.page.mockResolvedValue(page([entry(1, undefined, true)]));
    loader.refresh();
    await tick();
    expect(calls.page.mock.calls.every((c) => c[1].parentId === undefined)).toBe(true);
});

test('search cancellation, error retry, and a later successful page do not retain stale matches', async () => {
    const calls = api();
    calls.search
        .mockRejectedValueOnce(new Error('offline'))
        .mockResolvedValueOnce(page([entry(50)]));
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.search('old');
    await tick();
    expect(loader.branch()?.error).toBeTruthy();
    loader.retry();
    await tick();
    expect([...loader.matches]).toEqual([50]);
    calls.search.mockResolvedValueOnce(page([entry(60)]));
    loader.setVisible([50]);
    calls.nodes.mockResolvedValue([]);
    loader.refresh();
    await tick();
    expect([...loader.matches]).toEqual([60]);
    loader.search('');
    expect(loader.entries.has(50)).toBe(false);
});

test('all paths, pages and refresh reads share a four-request concurrency limit', async () => {
    const calls = api();
    const pending: (() => void)[] = [];
    calls.page.mockImplementation(
        () => new Promise((resolve) => pending.push(() => resolve(page([]))))
    );
    const loader = new TreeLoader('process', vi.fn(), calls);
    for (let i = 0; i < 20; i++) loader.toggle(i, true);
    expect(calls.page).toHaveBeenCalledTimes(4);
    pending.shift()!();
    await tick();
    expect(calls.page).toHaveBeenCalledTimes(5);
    loader.dispose();
    pending.forEach((r) => r());
    await tick();
    expect(calls.page).toHaveBeenCalledTimes(5);
});

test('refresh discovers the first root after an initially empty process', async () => {
    const calls = api();
    calls.page.mockResolvedValueOnce(page([])).mockResolvedValueOnce(page([entry(1)]));
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.start();
    await tick();
    expect(loader.segments).toHaveLength(0);
    loader.refresh();
    await tick();
    expect(loader.segments.map((e) => e.segment.id)).toEqual([1]);
    loader.dispose();
});

test('default selection and wrapped error navigation reach unloaded failures', async () => {
    const calls = api();
    calls.search.mockResolvedValueOnce(page([entry(99999, 1)], false, [entry(1, undefined, true)]));
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.selectDefault(false);
    await tick();
    expect(calls.search.mock.calls[0][1]).toMatchObject({ category: 'FAILED', limit: 1 });
    expect(loader.selectedId).toBe(99999);
    calls.search.mockResolvedValueOnce(page([])).mockResolvedValueOnce(page([entry(99999, 1)]));
    loader.navigateError(1);
    await tick();
    expect(calls.search.mock.calls[1][1]).toMatchObject({
        errorsOnly: true,
        afterId: 99999,
        limit: 1,
    });
    expect(calls.search.mock.calls[2][1].afterId).toBeUndefined();
    expect(loader.selectedId).toBe(99999);
    expect(calls.path).not.toHaveBeenCalled();
    loader.dispose();
});

test.each([
    { live: false, failed: false, reads: 2 },
    { live: false, failed: true, reads: 2 },
    { live: true, failed: false, reads: 3 },
    { live: true, failed: true, reads: 2 },
])(
    'initial selection reuses lookup context without viewport rereads: %j',
    async ({ live, failed, reads }) => {
        const calls = api();
        calls.page.mockImplementation(async (_, query) => {
            if (query.parentId === 1) return page([entry(10000, 1)]);
            return page([entry(1, undefined, true)]);
        });
        calls.search.mockImplementation(async (_, query) => {
            if (query.category === 'FAILED' && !failed) return page([]);
            return page([entry(10000, 1)], false, [entry(1, undefined, true)]);
        });
        const loader = new TreeLoader('process', vi.fn(), calls);
        loader.start();
        await tick();
        loader.selectDefault(live);
        await tick();
        loader.setVisible(loader.segments.map((e) => e.segment.id));
        loader.refreshViewport();
        await tick();
        expect(calls.page.mock.calls.length + calls.search.mock.calls.length).toBe(
            reads + (failed || live ? 1 : 0)
        );
        expect(calls.path).not.toHaveBeenCalled();
        expect(calls.nodes).not.toHaveBeenCalled();
        expect(loader.selectedId).toBe(failed || live ? 10000 : 1);
        loader.dispose();
    }
);

test('cached selection needs a complete path and cancels an obsolete path read', async () => {
    const calls = api();
    let resolve!: (entries: TreeEntry[]) => void;
    calls.path.mockImplementationOnce(() => new Promise((r) => (resolve = r)));
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.entries.set(3, entry(3, 2));
    loader.select(3);
    expect(calls.path).toHaveBeenCalledTimes(1);
    const signal = calls.path.mock.calls[0][2]!;
    loader.entries.set(1, entry(1));
    loader.select(1);
    expect(calls.path).toHaveBeenCalledTimes(1);
    expect(signal.aborted).toBe(true);
    resolve([entry(2), entry(3, 2)]);
    await tick();
    expect(loader.selectedId).toBe(1);
    expect(loader.entries.has(3)).toBe(false);
    loader.dispose();
});

test('scrolling reuses fresh pages but refreshes stale ones; manual refresh bypasses freshness', async () => {
    const calls = api();
    calls.page.mockResolvedValue(page([entry(1, undefined, true)]));
    let now = 0;
    const loader = new TreeLoader('process', vi.fn(), calls, () => now);
    loader.start();
    await tick();
    loader.setVisible([1]);
    now = 4999;
    loader.refreshViewport();
    await tick();
    expect(calls.page).toHaveBeenCalledTimes(1);
    now = 5000;
    loader.refreshViewport();
    await tick();
    expect(calls.page).toHaveBeenCalledTimes(2);
    loader.refreshViewport();
    await tick();
    expect(calls.page).toHaveBeenCalledTimes(2);
    loader.refresh();
    await tick();
    expect(calls.page).toHaveBeenCalledTimes(3);
    expect(calls.nodes).not.toHaveBeenCalled();
    loader.dispose();
});

test('refresh requests only IDs outside refreshed pages, coalescing overlapping pending reads', async () => {
    const calls = api();
    calls.page.mockResolvedValueOnce(page([entry(1, undefined, true)]));
    calls.path.mockResolvedValue([entry(1, undefined, true), entry(2, 1)]);
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.start();
    await tick();
    loader.entries.set(2, entry(2, 1));
    calls.page.mockResolvedValueOnce(page([]));
    loader.select(2);
    await tick();
    loader.toggle(1, false);
    calls.page.mockClear();
    loader.setVisible([1, 2]);
    let resolvePage!: (p: TreePage) => void;
    let resolveNodes!: (items: TreeEntry[]) => void;
    calls.page.mockImplementation(() => new Promise((r) => (resolvePage = r)));
    calls.nodes.mockImplementation(() => new Promise((r) => (resolveNodes = r)));
    loader.refresh();
    loader.refreshViewport();
    loader.refresh();
    expect(calls.page).toHaveBeenCalledTimes(1);
    expect(calls.nodes).toHaveBeenCalledTimes(1);
    expect(calls.nodes.mock.calls[0][1]).toEqual([2]);
    resolvePage(page([entry(1, undefined, true)]));
    resolveNodes([entry(2, 1)]);
    await tick();
    loader.refreshViewport();
    await tick();
    expect(calls.page).toHaveBeenCalledTimes(1);
    expect(calls.nodes).toHaveBeenCalledTimes(1);
    loader.dispose();
});

test('a selected search result that stops matching still gets fresh metadata', async () => {
    const calls = api();
    calls.search.mockResolvedValueOnce(
        page([{ ...entry(2, 1), category: 'FAILED' }], false, [entry(1)])
    );
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.search('', 'FAILED');
    await tick();
    loader.select(2);
    expect(calls.path).not.toHaveBeenCalled();
    calls.search.mockResolvedValue(page([]));
    calls.nodes.mockResolvedValue([entry(1), entry(2, 1)]);
    loader.setVisible([1, 2]);
    loader.refresh();
    await tick();
    expect(calls.nodes).toHaveBeenCalledTimes(1);
    expect(new Set(calls.nodes.mock.calls[0][1])).toEqual(new Set([1, 2]));
    expect(loader.entries.get(2)?.category).toBe('OK');
    expect(loader.selectedId).toBe(2);
    expect([...loader.matches]).toEqual([]);
    loader.dispose();
});

test('late default and error results cannot override a newer manual selection', async () => {
    const calls = api();
    let resolve!: (p: TreePage) => void;
    calls.search.mockImplementation(() => new Promise((r) => (resolve = r)));
    const loader = new TreeLoader('process', vi.fn(), calls);
    loader.entries.set(1, entry(1));
    loader.selectDefault(false);
    loader.select(1);
    resolve(page([entry(10)]));
    await tick();
    expect(loader.selectedId).toBe(1);
    expect(loader.entries.has(10)).toBe(false);
    loader.navigateError(1);
    loader.select(1);
    resolve(page([entry(20)]));
    await tick();
    expect(loader.selectedId).toBe(1);
    expect(loader.entries.has(20)).toBe(false);
    expect(calls.path).not.toHaveBeenCalled();
    loader.dispose();
});
