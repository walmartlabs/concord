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
import * as React from 'react';
import { act, cleanup, render } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { getSegmentLogPage } from '../logApi';
import { SegmentLogRequest, useSegmentLogs } from '../useSegmentLogs';

vi.mock('../logApi', async (importOriginal) => ({
    ...(await importOriginal<typeof import('../logApi')>()),
    getSegmentLogPage: vi.fn(),
}));

const INTERVAL = 1_000;

const Probe = ({ requests }: { requests: SegmentLogRequest[] }) => {
    const logs = useSegmentLogs('process', {
        pollInterval: INTERVAL,
        pollingEnabled: true,
        refreshToken: 0,
    });
    const { reconcile } = logs;
    React.useEffect(() => reconcile(requests), [reconcile, requests]);
    return null;
};

const request = (id: number, live: boolean): SegmentLogRequest => ({ id, live, tailBytes: 64 });
const calls = (id: number) =>
    vi.mocked(getSegmentLogPage).mock.calls.filter(([, segmentId]) => segmentId === id).length;
const settle = () => act(async () => {});

beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(getSegmentLogPage).mockImplementation(async (_id, _segmentId, query) => ({
        text: query.after === undefined ? 'a' : '',
        before: 0,
        after: 1,
        hasPrevious: false,
        hasNext: false,
    }));
});

afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.mocked(getSegmentLogPage).mockReset();
});

test('selection changes do not restart a running poll', async () => {
    const view = render(<Probe requests={[request(1, true)]} />);
    await settle();
    expect(calls(1)).toBe(1);

    for (let i = 0; i < 3; i++) {
        view.rerender(<Probe requests={[request(1, true), request(2 + i, true)]} />);
        await settle();
    }
    // Only initial reads of the new segments, no extra appends for the loaded one.
    expect(calls(1)).toBe(1);

    await act(async () => {
        await vi.advanceTimersByTimeAsync(INTERVAL);
    });
    expect(calls(1)).toBe(2);
});

test('new live work restarts a stopped poll', async () => {
    const view = render(<Probe requests={[request(1, false)]} />);
    await settle();
    await act(async () => {
        await vi.advanceTimersByTimeAsync(INTERVAL);
    });
    expect(calls(1)).toBe(1);

    view.rerender(<Probe requests={[request(2, true)]} />);
    await settle();
    expect(calls(2)).toBe(1);

    await act(async () => {
        await vi.advanceTimersByTimeAsync(INTERVAL);
    });
    expect(calls(2)).toBe(2);
});
