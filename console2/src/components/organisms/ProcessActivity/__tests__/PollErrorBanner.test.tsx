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
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';

import PollErrorBanner from '../PollErrorBanner';

afterEach(cleanup);

const error = { status: 503, message: 'Service Unavailable (503)', details: 'x'.repeat(4096) };

test('renders nothing without an error', () => {
    const view = render(
        <PollErrorBanner tabLabel="Events" retryInterval={5000} onRetry={() => undefined} />
    );
    expect(view.container.innerHTML).toBe('');
});

test('names the failed tab and omits error details', () => {
    render(
        <PollErrorBanner
            tabError={error}
            tabLabel="Events"
            retryInterval={5000}
            onRetry={() => undefined}
        />
    );
    expect(screen.getByText('Unable to refresh the Events tab')).toBeTruthy();
    expect(screen.getByText(/The data below may be out of date/)).toBeTruthy();
    expect(screen.getByText('Last error: Service Unavailable (503)')).toBeTruthy();
    expect(screen.queryByText(error.details)).toBeNull();
});

test('describes the idle polling interval', () => {
    render(
        <PollErrorBanner
            processError={error}
            tabLabel="Events"
            retryInterval={60_000}
            onRetry={() => undefined}
        />
    );
    expect(screen.getByText('Unable to refresh the process status')).toBeTruthy();
    expect(screen.getByText(/Retrying automatically every minute/).textContent).toMatch(
        /every minute, or retry now\.$/
    );
});

test('retries on demand', () => {
    const onRetry = vi.fn();
    render(
        <PollErrorBanner
            processError={error}
            tabError={error}
            tabLabel="Events"
            retryInterval={5000}
            onRetry={onRetry}
        />
    );
    expect(screen.getByText('Unable to refresh the process')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'retry now' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
});
