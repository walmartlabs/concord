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
import { usePolling } from '../usePolling';

const loading = vi.fn();
const Harness = ({
    request,
    refresh = false,
}: {
    request: () => Promise<boolean>;
    refresh?: boolean;
}) => {
    const error = usePolling(request, 1000, loading, refresh);
    return <div>{error?.message ?? 'healthy'}</div>;
};
const advance = async (ms = 0) => {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(ms);
    });
};
beforeEach(() => {
    vi.useFakeTimers();
    loading.mockClear();
});
afterEach(() => {
    cleanup();
    vi.useRealTimers();
});

test.each([0, 408, 429, 500, 502, 503, 504])(
    'recovers after a transient error with status %s',
    async (status) => {
        const request = vi
            .fn()
            .mockResolvedValueOnce(true)
            .mockRejectedValueOnce({ status, message: 'temporarily unavailable' })
            .mockResolvedValueOnce(false);
        render(<Harness request={request} />);
        await advance();
        await advance(1000);
        expect(screen.getByText('temporarily unavailable')).toBeTruthy();
        await advance(1000);
        expect(request).toHaveBeenCalledTimes(3);
        expect(screen.getByText('healthy')).toBeTruthy();
        await advance(5000);
        expect(request).toHaveBeenCalledTimes(3);
        expect(loading.mock.calls.reduce((total, [inc]) => total + inc, 0)).toBe(0);
    }
);

test('recovers from a network exception on the initial request', async () => {
    const request = vi
        .fn()
        .mockRejectedValueOnce(new TypeError('Failed to fetch'))
        .mockResolvedValueOnce(false);
    render(<Harness request={request} />);
    await advance();
    await advance(1000);
    expect(request).toHaveBeenCalledTimes(2);
    expect(screen.getByText('healthy')).toBeTruthy();
});

test.each([400, 401, 403, 404])('does not automatically retry status %s', async (status) => {
    const request = vi.fn().mockRejectedValue({ status, message: 'rejected' });
    render(<Harness request={request} />);
    await advance(5000);
    expect(request).toHaveBeenCalledTimes(1);
});

test('does not retry an aborted request', async () => {
    const request = vi.fn().mockRejectedValue(new DOMException('Aborted', 'AbortError'));
    render(<Harness request={request} />);
    await advance(5000);
    expect(request).toHaveBeenCalledTimes(1);
});

test('cancels scheduled retries on unmount', async () => {
    const request = vi.fn().mockRejectedValue({ status: 502 });
    const view = render(<Harness request={request} />);
    await advance();
    view.unmount();
    await advance(5000);
    expect(request).toHaveBeenCalledTimes(1);
});

test('an obsolete request cannot stop polling after refresh', async () => {
    let rejectOld!: (error: unknown) => void;
    const pending = new Promise<boolean>((_resolve, reject) => {
        rejectOld = reject;
    });
    const request = vi.fn().mockReturnValueOnce(pending).mockResolvedValue(true);
    const view = render(<Harness request={request} />);
    view.rerender(<Harness request={request} refresh={true} />);
    await advance();
    await act(async () => {
        rejectOld({ status: 404, message: 'obsolete error' });
    });
    await advance(1000);
    expect(request).toHaveBeenCalledTimes(3);
    expect(screen.getByText('healthy')).toBeTruthy();
});

test('an obsolete success cannot clear the current error or stop its retry', async () => {
    let resolveOld!: (result: boolean) => void;
    const pending = new Promise<boolean>((resolve) => {
        resolveOld = resolve;
    });
    const request = vi
        .fn()
        .mockReturnValueOnce(pending)
        .mockRejectedValueOnce({ status: 502, message: 'current error' })
        .mockResolvedValue(false);
    const view = render(<Harness request={request} />);
    view.rerender(<Harness request={request} refresh={true} />);
    await advance();
    await act(async () => {
        resolveOld(false);
    });
    expect(screen.getByText('current error')).toBeTruthy();
    await advance(1000);
    expect(request).toHaveBeenCalledTimes(3);
    expect(screen.getByText('healthy')).toBeTruthy();
});

test('settling a pending request after unmount does not schedule another request', async () => {
    let resolvePending!: (result: boolean) => void;
    const request = vi.fn().mockImplementation(
        () =>
            new Promise<boolean>((resolve) => {
                resolvePending = resolve;
            })
    );
    const view = render(<Harness request={request} />);
    view.unmount();
    await act(async () => {
        resolvePending(true);
    });
    await advance(5000);
    expect(request).toHaveBeenCalledTimes(1);
    expect(loading.mock.calls.reduce((total, [inc]) => total + inc, 0)).toBe(0);
});

test('waits for a pending request before scheduling the next poll', async () => {
    let resolvePending!: (result: boolean) => void;
    const request = vi
        .fn()
        .mockImplementationOnce(
            () =>
                new Promise<boolean>((resolve) => {
                    resolvePending = resolve;
                })
        )
        .mockResolvedValue(false);
    render(<Harness request={request} />);
    await advance(5000);
    expect(request).toHaveBeenCalledTimes(1);
    await act(async () => {
        resolvePending(true);
    });
    await advance(999);
    expect(request).toHaveBeenCalledTimes(1);
    await advance(1);
    expect(request).toHaveBeenCalledTimes(2);
});
