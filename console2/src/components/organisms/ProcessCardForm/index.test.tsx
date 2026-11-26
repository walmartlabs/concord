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
import React from 'react';
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ProcessCardForm from './index';
import { getProcessCardData, startProcessCard } from '../../../api/service/console/user';
import { PROTOCOL } from './bridge';

vi.mock('../../../api/service/console/user', () => ({
    getProcessCardData: vi.fn(),
    startProcessCard: vi.fn(),
}));

afterEach(() => {
    cleanup();
    vi.resetAllMocks();
});

it('initializes an opaque sandbox and ignores other windows, channels and request overrides', async () => {
    vi.mocked(getProcessCardData).mockResolvedValue({ values: { name: 'test' } });
    vi.mocked(startProcessCard).mockRejectedValue({
        status: 403,
        message: 'sensitive backend details',
    });
    const { container, unmount } = render(<ProcessCardForm cardId="card-id" />);
    const frame = container.querySelector('iframe')!;
    expect(frame.srcdoc).toContain('sandbox="allow-scripts"');
    expect(frame.srcdoc).not.toContain('allow-same-origin');
    expect(frame.srcdoc).toContain(
        `frame-src ${window.location.origin}/api/v1/processcard/card-id/form`
    );
    expect(frame.getAttribute('referrerpolicy')).toBe('no-referrer');
    const post = vi.spyOn(frame.contentWindow!, 'postMessage');
    await waitFor(() => expect(post).toHaveBeenCalledOnce());
    const init = post.mock.calls[0][0];
    expect(init).toMatchObject({
        protocol: PROTOCOL,
        type: 'init',
        data: { values: { name: 'test' } },
    });
    const message = {
        protocol: PROTOCOL,
        type: 'start',
        channel: init.channel,
        arguments: { name: 'x' },
    };
    const send = (data: unknown, source: MessageEventSource | null = frame.contentWindow) =>
        window.dispatchEvent(new MessageEvent('message', { source, origin: 'null', data }));
    send(message, window);
    send({ ...message, channel: 'wrong' });
    send({ ...message, url: '/api/v1/apikey' });
    send({ ...message, type: 'navigate', url: '//evil.example' });
    expect(startProcessCard).not.toHaveBeenCalled();
    await act(async () => {
        send(message);
    });
    expect(startProcessCard).toHaveBeenCalledWith('card-id', { name: 'x' });
    expect(post).toHaveBeenLastCalledWith(
        {
            protocol: PROTOCOL,
            channel: init.channel,
            type: 'error',
            message: 'Unable to start process. Check access and arguments.',
        },
        window.location.origin
    );
    act(() => {
        send({ protocol: PROTOCOL, channel: init.channel, type: 'invalidated' });
        send(message);
    });
    expect(startProcessCard).toHaveBeenCalledTimes(1);
    unmount();
    send(message);
    expect(startProcessCard).toHaveBeenCalledTimes(1);
});

it('invalidates the channel on subsequent loads and does not initialize a navigated document', async () => {
    vi.mocked(getProcessCardData).mockResolvedValue({});
    const { container } = render(<ProcessCardForm cardId="card-id" />);
    const frame = container.querySelector('iframe')!;
    const post = vi.spyOn(frame.contentWindow!, 'postMessage');
    await waitFor(() => expect(post).toHaveBeenCalledOnce());
    const init = post.mock.calls[0][0];
    fireEvent.load(frame);
    window.dispatchEvent(
        new MessageEvent('message', {
            source: frame.contentWindow,
            origin: 'null',
            data: { protocol: PROTOCOL, type: 'start', channel: init.channel, arguments: {} },
        })
    );
    expect(startProcessCard).not.toHaveBeenCalled();
    expect(getProcessCardData).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledOnce();
});
