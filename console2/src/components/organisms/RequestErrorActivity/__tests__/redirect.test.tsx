/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

vi.mock('../../../molecules', () => ({
    RequestErrorMessage: () => <div>Request failed</div>,
}));
// Keep unrelated Semantic UI animation timers out of the timer assertions.
vi.mock('semantic-ui-react', () => ({
    Dimmer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    Loader: ({ content }: { content: string }) => <div>{content}</div>,
}));

import RequestErrorActivity from '../index';

const unauthorized = { status: 401 };
const originalConcord = window.concord;

beforeEach(() => {
    vi.useFakeTimers();
    window.concord = { ...originalConcord, loginUrl: 'https://login.example/sign-in' };
});
afterEach(() => {
    cleanup();
    vi.clearAllTimers();
    vi.useRealTimers();
    window.concord = originalConcord;
});

const view = (status = 401) => (
    <MemoryRouter>
        <RequestErrorActivity error={{ status }} />
    </MemoryRouter>
);

test('a rerender does not schedule another external login redirect', () => {
    const rendered = render(view());
    expect(screen.getByText('Logging in')).toBeTruthy();
    expect(vi.getTimerCount()).toBe(1);
    rendered.rerender(view());
    expect(vi.getTimerCount()).toBe(1);
});

test('leaving the error page cancels the pending external redirect', () => {
    const rendered = render(view());
    expect(vi.getTimerCount()).toBe(1);
    rendered.unmount();
    expect(vi.getTimerCount()).toBe(0);
});

test('replacing the 401 with another error cancels the external redirect', () => {
    const rendered = render(view());
    rendered.rerender(view(503));
    expect(screen.getByText('Request failed')).toBeTruthy();
    expect(vi.getTimerCount()).toBe(0);
});

test('changing the external login URL replaces the pending redirect', () => {
    const rendered = render(view());
    window.concord = { ...window.concord, loginUrl: 'https://login.example/another' };
    rendered.rerender(view());
    expect(vi.getTimerCount()).toBe(1);
});

test('without an external login URL navigates to the local login with the return location', () => {
    window.concord = { ...window.concord, loginUrl: undefined };
    const Route = () => {
        const location = useLocation();
        return location.pathname === '/login' ? (
            <div>Login from {location.state.from.pathname}</div>
        ) : (
            <RequestErrorActivity error={unauthorized} />
        );
    };
    render(
        <MemoryRouter initialEntries={['/process/example/status']}>
            <Route />
        </MemoryRouter>
    );
    expect(screen.getByText('Login from /process/example/status')).toBeTruthy();
    expect(vi.getTimerCount()).toBe(0);
});
