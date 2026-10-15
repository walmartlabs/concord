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
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, expect, test, vi } from 'vitest';

vi.mock('../../../../api/process', async (importOriginal) => {
    const actual = await importOriginal<Record<string, unknown>>();
    return { ...actual, get: vi.fn(), getRoot: vi.fn() };
});
vi.mock('../../ansible/ProcessAnsibleActivity', () => ({ default: () => null }));
vi.mock('../../ProcessAttachmentsActivity', () => ({ default: () => null }));
vi.mock('../../ProcessChildrenActivity', () => ({ default: () => null }));
vi.mock('../../ProcessEventsActivity', () => ({ default: () => null }));
vi.mock('../../ProcessHistoryActivity', () => ({ default: () => null }));
vi.mock('../../ProcessLogActivity', () => ({ default: () => null }));
vi.mock('../../ProcessLogActivityV2', () => ({ default: () => null }));
vi.mock('../../ProcessLogTreeActivity', () => ({
    default: () => <div>retained routed content</div>,
}));
vi.mock('../../ProcessStatusActivity', () => ({ default: () => null }));
vi.mock('../../ProcessWaitActivity', () => ({ default: () => null }));
vi.mock('../../../pages/NotFoundPage', () => ({ default: () => null }));
vi.mock('../Toolbar', () => ({
    default: ({ process, refresh }: { process?: { instanceId: string }; refresh: () => void }) => (
        <div>
            <span>{process ? `process ${process.instanceId}` : 'loading process'}</span>
            <button type="button" onClick={refresh}>
                Toolbar refresh
            </button>
        </div>
    ),
}));
vi.mock('../favicon', () => ({ useStatusFavicon: () => undefined }));
vi.mock('react-idle-timer', () => ({ useIdleTimer: () => undefined }));

import { get, ProcessStatus } from '../../../../api/process';
import type { ProcessEntry } from '../../../../api/process';
import ProcessActivity from '../index';
import RequestErrorActivity from '../../RequestErrorActivity';

const process = {
    instanceId: 'process-id',
    status: ProcessStatus.RUNNING,
    runtime: 'concord-v2',
} as ProcessEntry;

const InitialErrorRoute = () => {
    const location = useLocation();
    return (
        <>
            <div>{location.pathname}</div>
            {location.pathname !== '/login' && (
                <ProcessActivity instanceId="process-id" activeTab="logTree" />
            )}
        </>
    );
};

afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
});

test('retains same-instance process content after a poll error and retries it', async () => {
    vi.mocked(get)
        .mockResolvedValueOnce(process)
        .mockRejectedValueOnce({
            status: 401,
            message: 'Authentication required (401)',
            details: 'Session expired',
        })
        .mockResolvedValueOnce(process);

    render(
        <MemoryRouter initialEntries={['/log-tree']}>
            <ProcessActivity instanceId="process-id" activeTab="logTree" />
        </MemoryRouter>
    );

    expect(await screen.findByText('process process-id')).toBeTruthy();
    expect(screen.getByText('retained routed content')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Toolbar refresh' }));
    expect(await screen.findByText('Authentication required (401)')).toBeTruthy();
    expect(screen.getByText('Session expired')).toBeTruthy();
    expect(screen.getByText('retained routed content')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(vi.mocked(get)).toHaveBeenCalledTimes(3));
    expect(screen.queryByText('Authentication required (401)')).toBeNull();
    expect(screen.getByText('retained routed content')).toBeTruthy();
});

test('redirects an initial authorization error to login', async () => {
    vi.mocked(get).mockRejectedValueOnce({
        status: 401,
        message: 'Authentication required (401)',
        details: 'Session expired',
    });

    render(
        <MemoryRouter initialEntries={['/log-tree']}>
            <InitialErrorRoute />
        </MemoryRouter>
    );

    expect(await screen.findByText('/login')).toBeTruthy();
    expect(screen.queryByText('Authentication required (401)')).toBeNull();
});

test('schedules and cleans up a configured external login redirect', async () => {
    const previousConcord = window.concord;
    Object.defineProperty(window, 'concord', {
        configurable: true,
        value: { ...previousConcord, loginUrl: 'https://login.example/sign-in' },
    });
    const setTimeout = vi.spyOn(window, 'setTimeout').mockReturnValue(17);
    const clearTimeout = vi.spyOn(window, 'clearTimeout').mockImplementation(() => undefined);

    try {
        const view = render(
            <MemoryRouter>
                <RequestErrorActivity
                    error={{ status: 401, message: 'Authentication required (401)' }}
                />
            </MemoryRouter>
        );

        expect(await screen.findByText('Logging in')).toBeTruthy();
        expect(setTimeout).toHaveBeenCalledWith(expect.any(Function), 1000);
        view.unmount();
        expect(clearTimeout).toHaveBeenCalledWith(17);
    } finally {
        Object.defineProperty(window, 'concord', {
            configurable: true,
            value: previousConcord,
        });
    }
});
