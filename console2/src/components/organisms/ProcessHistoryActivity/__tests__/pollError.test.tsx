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
import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

vi.mock('../../../../api/process', async (importOriginal) => {
    const actual = await importOriginal<Record<string, unknown>>();
    return { ...actual, get: vi.fn() };
});
vi.mock('../../../molecules', () => ({
    ProcessHistoryList: ({ data }: { data?: unknown[] }) => (
        <div>{data ? `history ${data.length}` : 'no history'}</div>
    ),
}));
vi.mock('../../RequestErrorActivity', () => ({
    default: ({ error }: { error: { message?: string } }) => <div>error: {error.message}</div>,
}));

import { get, ProcessStatus } from '../../../../api/process';
import type { ProcessEntry } from '../../../../api/process';
import ProcessHistoryActivity from '../index';

const running = {
    instanceId: 'p',
    status: ProcessStatus.RUNNING,
    statusHistory: [],
} as unknown as ProcessEntry;

const advance = async (ms = 0) => {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(ms);
    });
};

const renderTab = () =>
    render(
        <ProcessHistoryActivity
            instanceId="p"
            loadingHandler={() => undefined}
            forceRefresh={false}
            dataFetchInterval={1000}
        />
    );

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.useRealTimers();
});

test('keeps loaded content during a transient polling error', async () => {
    vi.mocked(get)
        .mockResolvedValueOnce(running)
        .mockRejectedValueOnce({ status: 503, message: 'Unavailable (503)' })
        .mockResolvedValueOnce({ ...running, status: ProcessStatus.FINISHED });
    renderTab();
    await advance();
    expect(screen.getByText('history 0')).toBeTruthy();
    await advance(1000);
    expect(screen.getByText('history 0')).toBeTruthy();
    expect(screen.queryByText(/error:/)).toBeNull();
    await advance(1000);
    expect(vi.mocked(get)).toHaveBeenCalledTimes(3);
});

test('shows the error page when the initial request fails', async () => {
    vi.mocked(get).mockRejectedValueOnce({ status: 503, message: 'Unavailable (503)' });
    renderTab();
    await advance();
    expect(screen.getByText('error: Unavailable (503)')).toBeTruthy();
});

test('replaces loaded content after a permanent error', async () => {
    vi.mocked(get)
        .mockResolvedValueOnce(running)
        .mockRejectedValueOnce({ status: 404, message: 'Not Found (404)' });
    renderTab();
    await advance();
    await advance(1000);
    expect(screen.getByText('error: Not Found (404)')).toBeTruthy();
    expect(screen.queryByText('history 0')).toBeNull();
});
