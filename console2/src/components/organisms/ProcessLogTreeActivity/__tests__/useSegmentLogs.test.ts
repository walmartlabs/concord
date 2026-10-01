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
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import type { ConcordId } from '../../../../api/common';
import type { LogChunk, LogRange } from '../../../../api/process/log';
import { SegmentLogLoader } from '../useSegmentLogs';
import type { SegmentLogRequest } from '../useSegmentLogs';

interface PendingCall {
    instanceId: ConcordId;
    segmentId: number;
    range: LogRange;
    signal?: AbortSignal;
    resolve: (chunk: LogChunk) => void;
    reject: (error: unknown) => void;
}

const controlledFetch = () => {
    const calls: PendingCall[] = [];
    const fetch = (
        instanceId: ConcordId,
        segmentId: number,
        range: LogRange,
        signal?: AbortSignal
    ): Promise<LogChunk> =>
        new Promise((resolve, reject) => {
            calls.push({ instanceId, segmentId, range, signal, resolve, reject });
        });
    return { calls, fetch };
};

const request = (
    id: number,
    options: Partial<Omit<SegmentLogRequest, 'id'>> = {}
): SegmentLogRequest => ({
    id,
    live: false,
    tailBytes: 64,
    full: false,
    ...options,
});
const chunk = (data: string, low: number, high: number, length = high): LogChunk => ({
    data,
    range: { unit: 'bytes', low, high, length },
});

const flush = async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
};

describe('SegmentLogLoader', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    test('does not advance the cursor after an empty suffix response', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1, { live: true })]);

        expect(calls[0].range).toEqual({ high: 64 });
        calls[0].resolve(chunk('', 0, 64, 0));
        await flush();

        loader.poll();
        expect(calls[1].range).toEqual({ high: 64 });
        calls[1].resolve(chunk('first line\n', 0, 11));
        await flush();

        expect(loader.get(1).text).toBe('first line\n');
    });

    test('an empty full response clears an earlier tail truncation', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1)]);
        calls[0].resolve(chunk('tail\n', 100, 105, 105));
        await flush();
        expect(loader.get(1).truncated).toBe(true);

        loader.reconcile([request(1, { full: true })]);
        expect(calls[1].range).toEqual({ low: 0 });
        calls[1].resolve(chunk('', 0, 0, 0));
        await flush();

        expect(loader.get(1).text).toBe('');
        expect(loader.get(1).truncated).toBe(false);
        loader.reconcile([request(1, { full: true })]);
        expect(calls).toHaveLength(2);
    });

    test('retains a complete leading line and advances from the exclusive high offset', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1, { live: true })]);

        calls[0].resolve(chunk('ERROR first\n continuation\n', 100, 126, 126));
        await flush();

        expect(loader.get(1)).toMatchObject({
            text: 'ERROR first\n continuation\n',
            truncated: true,
        });
        loader.poll();
        expect(calls[1].range).toEqual({ low: 62 });
    });

    test('performs exactly one final fetch after a live segment finishes', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1, { live: true })]);
        calls[0].resolve(chunk('start\n', 0, 6));
        await flush();

        loader.reconcile([request(1)]);
        expect(calls).toHaveLength(2);
        calls[1].resolve(chunk('start\nfinish\n', 0, 13));
        await flush();
        loader.reconcile([request(1)]);

        expect(calls).toHaveLength(2);
        expect(loader.get(1).text).toBe('start\nfinish\n');
    });

    test('keeps one four-request scheduler while the desired list grows', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([1, 2, 3, 4, 5].map((id) => request(id)));
        expect(calls.map((call) => call.segmentId)).toEqual([1, 2, 3, 4]);

        loader.reconcile([1, 2, 3, 4, 5, 6].map((id) => request(id)));
        expect(calls).toHaveLength(4);

        calls[0].resolve(chunk('one\n', 0, 4));
        await flush();
        expect(calls.map((call) => call.segmentId)).toEqual([1, 2, 3, 4, 5]);
        expect(calls.filter((call) => call.segmentId === 1)).toHaveLength(1);
    });

    test('ignores an aborted response before loading the reselected segment', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1)]);
        loader.reconcile([]);
        expect(calls[0].signal?.aborted).toBe(true);

        loader.reconcile([request(1)]);
        expect(calls).toHaveLength(2);

        calls[0].resolve(chunk('stale\n', 0, 6));
        await flush();
        expect(calls).toHaveLength(2);
        expect(loader.get(1).text).toBe('');

        calls[1].resolve(chunk('current\n', 0, 8));
        await flush();
        expect(loader.get(1).text).toBe('current\n');
    });

    test('upgrades a child preview once and never downgrades it', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1, { tailBytes: 64 })]);
        calls[0].resolve(chunk('preview', 100, 107, 400));
        await flush();

        loader.reconcile([request(1, { tailBytes: 256 })]);
        expect(calls[1].range).toEqual({ low: 0 });
        calls[1].resolve(chunk('complete preview', 0, 16, 400));
        await flush();

        loader.reconcile([request(1, { tailBytes: 64 })]);
        loader.reconcile([request(1, { tailBytes: 256 })]);
        expect(calls).toHaveLength(2);
    });

    test('does not reload a promoted preview whose live coverage already exceeds the larger tail', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1, { live: true, tailBytes: 64 })]);
        calls[0].resolve(chunk('a'.repeat(64), 100, 164, 164));
        await flush();

        loader.poll();
        calls[1].resolve(chunk('a'.repeat(64) + 'b'.repeat(300), 100, 464, 464));
        await flush();
        loader.reconcile([request(1, { live: true, tailBytes: 256 })]);

        expect(calls).toHaveLength(2);
        expect(loader.get(1).text).toHaveLength(364);
    });

    test('a coverage upgrade keeps old lines when output grows before the response', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1, { tailBytes: 4 })]);
        calls[0].resolve(chunk('old\n', 100, 104, 104));
        await flush();

        loader.reconcile([request(1, { tailBytes: 8 })]);
        expect(calls[1].range).toEqual({ low: 96 });
        calls[1].resolve(chunk('prefix\nold\nnew\n', 96, 111, 111));
        await flush();

        expect(loader.get(1).text).toContain('old\n');
        expect(loader.get(1).text).toContain('new\n');
    });

    test('returns to a bounded tail after a one-shot full load', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1, { live: true, tailBytes: 4 })]);
        calls[0].resolve(chunk('πtail', 100, 106, 106));
        await flush();

        loader.reconcile([request(1, { live: true, tailBytes: 4, full: true })]);
        expect(calls[1].range).toEqual({ low: 0 });
        calls[1].resolve(chunk('prefix\nπtail', 0, 13, 13));
        await flush();
        loader.reconcile([request(1, { live: true, tailBytes: 4 })]);

        loader.poll();
        expect(calls[2].range).toEqual({ low: 9 });
        calls[2].resolve(chunk('tailnew\n', 9, 17, 17));
        await flush();

        expect(loader.get(1)).toMatchObject({
            text: 'tailnew\n',
            truncated: true,
        });
        expect(loader.get(1).full).toBe(false);
        expect(calls).toHaveLength(3);
    });

    test('reuses completed data across same-process reconciliation', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1)]);
        calls[0].resolve(chunk('cached\n', 0, 7));
        await flush();

        loader.reconcile([]);
        loader.reconcile([request(1)]);

        expect(calls).toHaveLength(1);
        expect(loader.get(1).text).toBe('cached\n');
    });

    test('dispose aborts work and suppresses updates', async () => {
        const { calls, fetch } = controlledFetch();
        const onChange = vi.fn();
        const loader = new SegmentLogLoader('process', onChange, fetch);
        loader.reconcile([request(1)]);
        loader.dispose();
        calls[0].resolve(chunk('late\n', 0, 5));
        await flush();

        expect(calls[0].signal?.aborted).toBe(true);
        expect(onChange).not.toHaveBeenCalled();
        expect(loader.get(1).text).toBe('');
    });

    test('separate process loaders isolate segment zero', async () => {
        const first = controlledFetch();
        const second = controlledFetch();
        const firstLoader = new SegmentLogLoader('first', vi.fn(), first.fetch);
        const secondLoader = new SegmentLogLoader('second', vi.fn(), second.fetch);
        firstLoader.reconcile([request(0)]);
        secondLoader.reconcile([request(0)]);
        first.calls[0].resolve(chunk('first\n', 0, 6));
        second.calls[0].resolve(chunk('second\n', 0, 7));
        await flush();

        expect(firstLoader.get(0).text).toBe('first\n');
        expect(secondLoader.get(0).text).toBe('second\n');
    });

    test('retry preserves prior text and clears the displayed error', async () => {
        const { calls, fetch } = controlledFetch();
        const loader = new SegmentLogLoader('process', vi.fn(), fetch);
        loader.reconcile([request(1, { live: true })]);
        calls[0].resolve(chunk('kept\n', 0, 5));
        await flush();

        loader.refresh();
        calls[1].reject({ status: 401, message: 'Unauthorized (401)', details: 'expired' });
        await flush();
        expect(loader.get(1)).toMatchObject({ text: 'kept\n', error: { status: 401 } });

        loader.poll();
        expect(calls).toHaveLength(2);

        loader.retry(1);
        expect(loader.get(1).text).toBe('kept\n');
        expect(loader.get(1).error).toBeUndefined();
        expect(calls).toHaveLength(3);
    });
});
