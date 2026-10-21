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
import { SegmentLogLoader, SegmentLogRequest } from '../useSegmentLogs';
import { SegmentLogPage, SegmentLogPageQuery } from '../logApi';

const request = (id: number, live = false): SegmentLogRequest => ({
    id,
    live,
    tailBytes: 64,
});
const page = (
    text: string,
    before = 0,
    after = before + text.length,
    hasNext = false
): SegmentLogPage => ({
    text,
    before,
    after,
    hasPrevious: before > 0,
    hasNext,
});
const controlledFetch = () => {
    const calls: {
        id: number;
        query: SegmentLogPageQuery;
        signal?: AbortSignal;
        resolve: (p: SegmentLogPage) => void;
        reject: (e: unknown) => void;
    }[] = [];
    const fetch = (
        _instance: string,
        id: number,
        query: SegmentLogPageQuery,
        signal?: AbortSignal
    ) =>
        new Promise<SegmentLogPage>((resolve, reject) =>
            calls.push({ id, query, signal, resolve, reject })
        );
    return { calls, fetch };
};
const flush = async () => {
    for (let i = 0; i < 8; i++) await Promise.resolve();
};

describe('SegmentLogLoader pages', () => {
    test('resumes polling a cached completed segment after checkpoint restore', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('p', vi.fn(), fetch);
        loader.reconcile([request(1)]);
        calls[0].resolve(page('old\n'));
        await flush();
        loader.poll();
        expect(calls).toHaveLength(1);

        loader.reconcile([request(1, true)]);
        loader.poll();
        expect(calls[1].query).toEqual({ after: 4, limit: 64 });
        calls[1].resolve(page('resumed\n', 4));
        await flush();
        expect(loader.get(1).text).toBe('old\nresumed\n');

        loader.reconcile([request(1)]);
        calls[2].resolve(page('done\n', 12));
        await flush();
        loader.poll();
        expect(calls).toHaveLength(3);
        expect(loader.get(1).text).toBe('old\nresumed\ndone\n');
        loader.dispose();
    });

    test('prepends history and appends new output without replacing or duplicating it', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('p', vi.fn(), fetch);
        loader.reconcile([request(1, true)]);
        expect(calls[0].query).toEqual({ limit: 64 });
        calls[0].resolve(page('tail\n', 100, 105));
        await flush();
        loader.previous(1);
        expect(calls[1].query).toEqual({ before: 100, limit: 64 });
        calls[1].resolve(page('earlier\n', 92, 100));
        await flush();
        loader.poll();
        expect(calls[2].query).toEqual({ after: 105, limit: 64 });
        calls[2].resolve(page('new\n', 105, 109));
        await flush();
        expect(loader.get(1).text).toBe('earlier\ntail\nnew\n');
        expect(loader.get(1).before).toBe(92);
    });
    test('empty responses keep the cursor, even if data is appended concurrently', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('p', vi.fn(), fetch);
        loader.reconcile([request(1, true)]);
        calls[0].resolve(page(''));
        await flush();
        loader.poll();
        expect(calls[1].query.after).toBe(0);
        calls[1].resolve(page('first\n'));
        await flush();
        expect(loader.get(1).text).toBe('first\n');
    });
    test('drains a final burst in bounded pages and then stops', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('p', vi.fn(), fetch);
        loader.reconcile([request(1, true)]);
        calls[0].resolve(page('a', 0, 1));
        await flush();
        loader.reconcile([request(1)]);
        calls[1].resolve(page('b', 1, 2, true));
        await flush();
        expect(calls[2].query).toEqual({ after: 2, limit: 64 });
        calls[2].resolve(page('c', 2, 3));
        await flush();
        loader.reconcile([request(1)]);
        expect(calls).toHaveLength(3);
        expect(loader.get(1).text).toBe('abc');
    });
    test('keeps four concurrent requests, aborts deselected work and ignores stale responses', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('p', vi.fn(), fetch);
        loader.reconcile([1, 2, 3, 4, 5, 6].map((id) => request(id)));
        expect(calls).toHaveLength(4);
        loader.reconcile([request(5), request(6)]);
        expect(calls[0].signal?.aborted).toBe(true);
        expect(calls).toHaveLength(6);
        calls[0].resolve(page('stale'));
        await flush();
        expect(loader.get(1).text).toBe('');
        calls[4].resolve(page('cached'));
        await flush();
        loader.reconcile([]);
        loader.reconcile([request(5)]);
        expect(calls).toHaveLength(6);
        expect(loader.get(5).text).toBe('cached');
    });
    test('retry preserves history and retries the failed direction', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('p', vi.fn(), fetch);
        loader.reconcile([request(1)]);
        calls[0].resolve(page('tail', 100, 104));
        await flush();
        loader.previous(1);
        calls[1].reject({ status: 500, message: 'failed' });
        await flush();
        expect(loader.get(1).text).toBe('tail');
        loader.retry(1);
        expect(calls[2].query.before).toBe(100);
        calls[2].resolve(page('prefix', 94, 100));
        await flush();
        expect(loader.get(1).text).toBe('prefixtail');
        expect(loader.get(1).error).toBeUndefined();
    });
    test('one final read is queued when completion arrives during a live request', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('p', vi.fn(), fetch);
        loader.reconcile([request(1, true)]);
        loader.reconcile([request(1)]);
        calls[0].resolve(page('a'));
        await flush();
        expect(calls).toHaveLength(2);
        calls[1].resolve(page('b', 1, 2));
        await flush();
        loader.reconcile([request(1)]);
        expect(calls).toHaveLength(2);
    });
    test('dispose suppresses delayed updates', async () => {
        const { calls, fetch } = controlledFetch();
        const changed = vi.fn();
        const loader = new SegmentLogLoader('p', changed, fetch);
        loader.reconcile([request(1)]);
        loader.dispose();
        calls[0].resolve(page('late'));
        await flush();
        expect(changed).not.toHaveBeenCalled();
    });
});
