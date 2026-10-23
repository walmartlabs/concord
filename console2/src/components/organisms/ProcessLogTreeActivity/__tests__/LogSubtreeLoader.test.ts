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
import { describe, expect, test, vi } from 'vitest';
import { LOG_SEGMENTS_PAGE, LogSubtreeLoader } from '../LogSubtreeLoader';
import { TreeEntry, TreePage } from '../treeApi';
import { SegmentStatus } from '../../../../api/process/log';
const entry = (id: number): TreeEntry => ({
    segment: { id, parentId: 1, name: `step-${id}`, createdAt: '', status: SegmentStatus.OK },
    hasChildren: false,
    hasIterations: false,
    retried: false,
    category: 'OK',
});
const page = (ids: number[], next = false): TreePage => ({
    items: ids.map(entry),
    ancestors: [entry(1)],
    next,
    nextId: ids[ids.length - 1],
});
describe('LogSubtreeLoader', () => {
    test('keeps backwards and forwards cursors independent and drains a multi-page burst', async () => {
        const api = {
            descendants: vi
                .fn()
                .mockResolvedValueOnce(page([50, 49], true))
                .mockResolvedValueOnce(page([51, 52], true))
                .mockResolvedValueOnce(page([53]))
                .mockResolvedValueOnce(page([48, 47], true)),
            nodes: vi.fn().mockResolvedValue([]),
        };
        const loader = new LogSubtreeLoader('p', 1, vi.fn(), api);
        await loader.load();
        await loader.refresh();
        await loader.previous();
        expect(api.descendants.mock.calls.map((c) => c[2])).toEqual([
            { beforeId: undefined, limit: LOG_SEGMENTS_PAGE },
            { afterId: 50, limit: LOG_SEGMENTS_PAGE },
            { afterId: 52, limit: LOG_SEGMENTS_PAGE },
            { beforeId: 49, limit: LOG_SEGMENTS_PAGE },
        ]);
        expect([...loader.included].sort((a, b) => a - b)).toEqual([1, 47, 48, 49, 50, 51, 52, 53]);
    });
    test('late selection results cannot populate the new pane', async () => {
        let resolve!: (p: TreePage) => void;
        const api = {
            descendants: vi.fn().mockReturnValue(new Promise<TreePage>((r) => (resolve = r))),
            nodes: vi.fn(),
        };
        const changed = vi.fn();
        const loader = new LogSubtreeLoader('p', 1, changed, api);
        const pending = loader.load();
        loader.dispose();
        resolve(page([2]));
        await pending;
        expect(loader.entries.size).toBe(0);
        expect(changed).toHaveBeenCalledTimes(1);
    });
    test('an older-page error leaves the current window intact and retry uses the old cursor', async () => {
        const api = {
            descendants: vi
                .fn()
                .mockResolvedValueOnce(page([50], true))
                .mockRejectedValueOnce({ status: 500, message: 'failed' })
                .mockResolvedValueOnce(page([49])),
            nodes: vi.fn(),
        };
        const loader = new LogSubtreeLoader('p', 1, vi.fn(), api);
        await loader.load();
        await loader.previous();
        expect(loader.included.has(50)).toBe(true);
        expect(loader.error).toBeTruthy();
        await loader.retry();
        expect(api.descendants.mock.calls[2][2].beforeId).toBe(50);
        expect(loader.included.has(49)).toBe(true);
    });
});
