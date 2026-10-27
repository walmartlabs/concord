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
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, expect, test, vi } from 'vitest';

vi.mock('../../../../api/process', async (importOriginal) => {
    const actual = await importOriginal<Record<string, unknown>>();
    return { ...actual, get: vi.fn(), getRoot: vi.fn() };
});
const eventsRequest = vi.hoisted(() => vi.fn<() => Promise<boolean>>());

vi.mock('../../index', async () => {
    const { isBlockingPollingError, usePolling } = await import('../../../../api/usePolling');
    const ProcessEventsActivity = ({
        loadingHandler,
        forceRefresh,
        dataFetchInterval,
    }: {
        loadingHandler: (inc: number) => void;
        forceRefresh: boolean;
        dataFetchInterval: number;
    }) => {
        const error = usePolling(eventsRequest, dataFetchInterval, loadingHandler, forceRefresh);
        return <div>{isBlockingPollingError(error) ? 'events error page' : 'events content'}</div>;
    };
    return {
        ProcessStatusActivity: () => (
            <div>
                retained routed content
                <input aria-label="Tab state" defaultValue="" />
            </div>
        ),
        ProcessAnsibleActivity: () => null,
        ProcessAttachmentsActivity: () => null,
        ProcessChildrenActivity: () => null,
        ProcessEventsActivity,
        ProcessHistoryActivity: () => null,
        ProcessLogActivity: () => null,
        ProcessLogActivityV2: () => null,
        ProcessWaitActivity: () => null,
    };
});
vi.mock('../../../pages', () => ({ NotFoundPage: () => null }));
vi.mock('../../../molecules', async () => ({
    RequestErrorMessage: (await import('../../../molecules/RequestErrorMessage')).default,
}));
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

const process = {
    instanceId: 'process-id',
    status: ProcessStatus.RUNNING,
    runtime: 'concord-v2',
} as ProcessEntry;

const ErrorRoute = () => {
    const location = useLocation();
    return (
        <>
            <div>{location.pathname}</div>
            {location.pathname !== '/login' && (
                <ProcessActivity instanceId="process-id" activeTab="status" />
            )}
        </>
    );
};

afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.clearAllMocks();
    vi.useRealTimers();
});

test.each([0, 408, 429, 500, 502, 503])(
    'retains content after transient status %s and retries',
    async (status) => {
        vi.mocked(get)
            .mockResolvedValueOnce(process)
            .mockRejectedValueOnce({
                status,
                message: 'Bad Gateway (502)',
                details: 'Temporarily unavailable',
            })
            .mockResolvedValueOnce(process);

        render(
            <MemoryRouter initialEntries={['/status']}>
                <ProcessActivity instanceId="process-id" activeTab="status" />
            </MemoryRouter>
        );

        expect(await screen.findByText('process process-id')).toBeTruthy();
        expect(screen.getByText('retained routed content')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', { name: 'Toolbar refresh' }));
        expect(await screen.findByText('Bad Gateway (502)')).toBeTruthy();
        expect(screen.getByText('Temporarily unavailable')).toBeTruthy();
        expect(screen.getByText('retained routed content')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
        await waitFor(() => expect(vi.mocked(get)).toHaveBeenCalledTimes(3));
        expect(screen.queryByText('Bad Gateway (502)')).toBeNull();
        expect(screen.getByText('retained routed content')).toBeTruthy();
    }
);

test('redirects an initial authorization error to login', async () => {
    vi.mocked(get).mockRejectedValueOnce({
        status: 401,
        message: 'Authentication required (401)',
        details: 'Session expired',
    });

    render(
        <MemoryRouter initialEntries={['/status']}>
            <ErrorRoute />
        </MemoryRouter>
    );

    expect(await screen.findByText('/login')).toBeTruthy();
    expect(screen.queryByText('Authentication required (401)')).toBeNull();
});

test('redirects an expired session to login after process content was loaded', async () => {
    vi.mocked(get).mockResolvedValueOnce(process).mockRejectedValueOnce({
        status: 401,
        message: 'Authentication required (401)',
        details: 'Session expired',
    });

    render(
        <MemoryRouter initialEntries={['/status']}>
            <ErrorRoute />
        </MemoryRouter>
    );

    expect(await screen.findByText('process process-id')).toBeTruthy();
    expect(screen.getByText('retained routed content')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Toolbar refresh' }));

    expect(await screen.findByText('/login')).toBeTruthy();
    expect(screen.queryByText('retained routed content')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull();
});

test.each([403, 404])('replaces loaded content after status %s', async (status) => {
    vi.mocked(get)
        .mockResolvedValueOnce(process)
        .mockRejectedValueOnce({ status, message: 'Access unavailable' });
    render(
        <MemoryRouter initialEntries={['/status']}>
            <ProcessActivity instanceId="process-id" activeTab="status" />
        </MemoryRouter>
    );
    expect(await screen.findByText('process process-id')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Toolbar refresh' }));
    expect(await screen.findByText('Access unavailable')).toBeTruthy();
    expect(screen.queryByText('retained routed content')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull();
});

test('does not retain the previous process when navigating to another instance', async () => {
    vi.mocked(get)
        .mockResolvedValueOnce(process)
        .mockRejectedValueOnce({ status: 503, message: 'New process unavailable' });
    const view = render(
        <MemoryRouter initialEntries={['/status']}>
            <ProcessActivity instanceId="process-id" activeTab="status" />
        </MemoryRouter>
    );
    expect(await screen.findByText('process process-id')).toBeTruthy();
    view.rerender(
        <MemoryRouter initialEntries={['/status']}>
            <ProcessActivity instanceId="another-id" activeTab="status" />
        </MemoryRouter>
    );
    expect(await screen.findByText('New process unavailable')).toBeTruthy();
    expect(screen.queryByText('process process-id')).toBeNull();
    expect(screen.queryByText('retained routed content')).toBeNull();
});

test('shows an error page when the initial request fails', async () => {
    vi.mocked(get).mockRejectedValueOnce({ status: 503, message: 'Initial failure' });
    render(
        <MemoryRouter initialEntries={['/status']}>
            <ProcessActivity instanceId="process-id" activeTab="status" />
        </MemoryRouter>
    );
    expect(await screen.findByText('Initial failure')).toBeTruthy();
    expect(screen.queryByText('retained routed content')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull();
});

test('removes the banner after automatic polling recovers without remounting the tab', async () => {
    vi.useFakeTimers();
    vi.mocked(get)
        .mockResolvedValueOnce(process)
        .mockRejectedValueOnce({ status: 503, message: 'Temporary failure' })
        .mockResolvedValueOnce({ ...process, status: ProcessStatus.FINISHED });
    render(
        <MemoryRouter initialEntries={['/status']}>
            <ProcessActivity instanceId="process-id" activeTab="status" />
        </MemoryRouter>
    );
    await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
    });
    fireEvent.change(screen.getByLabelText('Tab state'), { target: { value: 'keep me' } });
    await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
    });
    expect(screen.getByText('Temporary failure')).toBeTruthy();
    expect((screen.getByLabelText('Tab state') as HTMLInputElement).value).toBe('keep me');
    await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
    });
    expect(screen.queryByText('Temporary failure')).toBeNull();
    expect((screen.getByLabelText('Tab state') as HTMLInputElement).value).toBe('keep me');
    expect(vi.mocked(get)).toHaveBeenCalledTimes(3);
});

test('shows a tab polling error in the page banner while the process request succeeds', async () => {
    vi.useFakeTimers();
    vi.mocked(get).mockResolvedValue(process);
    eventsRequest
        .mockResolvedValueOnce(true)
        .mockRejectedValueOnce({ status: 503, message: 'Service Unavailable (503)' })
        .mockResolvedValue(true);
    render(
        <MemoryRouter initialEntries={['/events']}>
            <ProcessActivity instanceId="process-id" activeTab="events" />
        </MemoryRouter>
    );
    await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByText('events content')).toBeTruthy();
    expect(screen.queryByText('Service Unavailable (503)')).toBeNull();

    await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
    });
    expect(screen.getByText('Service Unavailable (503)')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeTruthy();
    expect(screen.getByText('events content')).toBeTruthy();

    await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
    });
    expect(screen.queryByText('Service Unavailable (503)')).toBeNull();
    expect(screen.getByText('events content')).toBeTruthy();
});

test('shows a single banner when both the process and the tab fail', async () => {
    vi.useFakeTimers();
    vi.mocked(get)
        .mockResolvedValueOnce(process)
        .mockRejectedValueOnce({ status: 502, message: 'Process poll failed (502)' })
        .mockResolvedValue(process);
    eventsRequest
        .mockResolvedValueOnce(true)
        .mockRejectedValueOnce({ status: 502, message: 'Events poll failed (502)' })
        .mockResolvedValue(true);
    render(
        <MemoryRouter initialEntries={['/events']}>
            <ProcessActivity instanceId="process-id" activeTab="events" />
        </MemoryRouter>
    );
    await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
    });
    await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
    });
    expect(screen.getByText('Process poll failed (502)')).toBeTruthy();
    expect(screen.queryByText('Events poll failed (502)')).toBeNull();
    expect(screen.getAllByRole('button', { name: 'Retry' })).toHaveLength(1);
});

test('does not report a blocking tab error to the page banner', async () => {
    vi.mocked(get).mockResolvedValue(process);
    eventsRequest.mockRejectedValue({ status: 503, message: 'Initial events failure' });
    render(
        <MemoryRouter initialEntries={['/events']}>
            <ProcessActivity instanceId="process-id" activeTab="events" />
        </MemoryRouter>
    );
    expect(await screen.findByText('events error page')).toBeTruthy();
    expect(screen.queryByText('Initial events failure')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull();
});
