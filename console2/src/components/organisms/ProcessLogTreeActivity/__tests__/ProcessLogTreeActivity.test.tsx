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
import { useState } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

vi.mock('../../../../api/process/log', async (importOriginal) => {
    const actual = await importOriginal<Record<string, unknown>>();
    return { ...actual, listLogSegments: vi.fn() };
});
vi.mock('../LogSegmentTree', () => ({ default: () => <div>retained tree</div> }));
vi.mock('../StepHeader', () => ({ default: () => <div>retained header</div> }));
const viewMounts = vi.hoisted(() => ({ count: 0 }));
vi.mock('../LogView', () => ({
    default: ({ node }: { node?: { segment: { id: number } } }) => {
        React.useEffect(() => {
            viewMounts.count++;
        }, []);
        return <div>{node ? `retained log ${node.segment.id}` : 'no selection'}</div>;
    },
}));

import { ProcessStatus } from '../../../../api/process';
import { listLogSegments, SegmentStatus } from '../../../../api/process/log';
import type { LogSegmentEntry } from '../../../../api/process/log';
import ProcessLogTreeActivity from '../index';

const segment: LogSegmentEntry = {
    id: 0,
    name: 'system',
    createdAt: new Date(0).toISOString(),
    status: SegmentStatus.RUNNING,
};

const Harness = () => {
    const [refresh, setRefresh] = useState(false);
    return (
        <>
            <button type="button" onClick={() => setRefresh((value) => !value)}>
                Trigger refresh
            </button>
            <ProcessLogTreeActivity
                instanceId="process"
                processStatus={ProcessStatus.RUNNING}
                loadingHandler={vi.fn()}
                forceRefresh={refresh}
                onRefresh={() => setRefresh((value) => !value)}
                dataFetchInterval={60_000}
            />
        </>
    );
};

beforeEach(() => {
    viewMounts.count = 0;
    vi.stubGlobal(
        'ResizeObserver',
        class {
            observe() {}
            disconnect() {}
        }
    );
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
        callback(0);
        return 0;
    });
});

afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

test('retains the tree and selected viewer after a segment-list error', async () => {
    vi.mocked(listLogSegments)
        .mockResolvedValueOnce({ items: [segment], next: false })
        .mockRejectedValueOnce({
            status: 401,
            message: 'Authentication required (401)',
            details: 'Session expired',
        })
        .mockResolvedValueOnce({ items: [segment], next: false });

    render(
        <MemoryRouter initialEntries={['/log-tree']}>
            <Harness />
        </MemoryRouter>
    );

    expect(await screen.findByText('retained log 0')).toBeTruthy();
    expect(screen.getByText('retained tree')).toBeTruthy();
    expect(viewMounts.count).toBe(1);

    fireEvent.click(screen.getByRole('button', { name: 'Trigger refresh' }));
    expect(await screen.findByText('Authentication required (401)')).toBeTruthy();
    expect(screen.getByText('Session expired')).toBeTruthy();
    expect(screen.getByText('retained log 0')).toBeTruthy();
    expect(viewMounts.count).toBe(1);

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(vi.mocked(listLogSegments)).toHaveBeenCalledTimes(3));
    expect(screen.queryByText('Authentication required (401)')).toBeNull();
    expect(screen.getByText('retained log 0')).toBeTruthy();
    expect(viewMounts.count).toBe(1);
});
