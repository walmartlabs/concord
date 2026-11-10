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
import { afterEach, describe, expect, test, vi } from 'vitest';

import { managedFetch, toRequestError } from '../common';
import { getLog, getSegmentLog } from '../process/log';

describe('managedFetch errors', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    test('preserves a JSON error message, status, and details', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            new Response(
                JSON.stringify({ message: 'Authentication required', details: 'Session expired' }),
                {
                    status: 401,
                    statusText: 'Unauthorized',
                    headers: { 'Content-Type': 'application/json' },
                }
            )
        );

        await expect(managedFetch('/api/test')).rejects.toMatchObject({
            message: 'Authentication required (401)',
            details: 'Session expired',
            status: 401,
        });
    });

    test('uses the HTTP status as the heading and keeps plain text in details', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            new Response('Access denied', {
                status: 403,
                statusText: 'Forbidden',
                headers: { 'Content-Type': 'text/plain' },
            })
        );

        await expect(managedFetch('/api/test')).rejects.toEqual({
            message: 'Forbidden (403)',
            details: 'Access denied',
            status: 403,
        });
    });

    test('uses the HTTP status text for an empty response', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            new Response(null, {
                status: 500,
                statusText: 'Internal Server Error',
                headers: { 'Content-Length': '0' },
            })
        );

        await expect(managedFetch('/api/test')).rejects.toEqual({
            message: 'Internal Server Error (500)',
            status: 500,
        });
    });

    test('normalizes a network failure with status zero', async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => undefined);
        vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

        await expect(managedFetch('/api/test')).rejects.toEqual({
            message: 'Failed to fetch (0)',
            status: 0,
        });
    });

    test('preserves an Error named AbortError', async () => {
        const error = new Error('The operation was aborted');
        error.name = 'AbortError';
        vi.spyOn(globalThis, 'fetch').mockRejectedValue(error);

        await expect(managedFetch('/api/test')).rejects.toBe(error);
    });

    test.each([
        'first line\nsecond line',
        'x'.repeat(4096),
        '<html>\n<h1>Bad Gateway</h1>\n</html>',
    ])('keeps a text response out of the error heading', async (body) => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            new Response(body, {
                status: 502,
                statusText: 'Bad Gateway',
                headers: { 'Content-Type': 'text/plain' },
            })
        );
        await expect(managedFetch('/api/test')).rejects.toEqual({
            message: 'Bad Gateway (502)',
            details: body,
            status: 502,
        });
    });

    test.each([
        [[{ message: 'Invalid value' }], 'Bad Request (400)', 'Invalid value'],
        [{ message: 'Invalid value', details: 'Invalid value' }, 'Bad Request (400)', 'Invalid value'],
        [
            { message: 'Invalid value', details: 'Field: name' },
            'Bad Request (400)',
            'Field: name',
        ],
        [null, 'Bad Request (400)', undefined],
        [[], 'Bad Request (400)', undefined],
    ])(
        'normalizes Siesta errors without duplicating the message: %j',
        async (body, message, details) => {
            vi.spyOn(globalThis, 'fetch').mockResolvedValue(
                new Response(JSON.stringify(body), {
                    status: 400,
                    statusText: 'Bad Request',
                    headers: {
                        'Content-Type': 'application/vnd.concord-validation-errors-v1+json',
                    },
                })
            );
            await expect(managedFetch('/api/test')).rejects.toMatchObject({
                message,
                details,
                status: 400,
            });
        }
    );

    test.each(['null', '{invalid json'])(
        'falls back to the HTTP status for a JSON body %s',
        async (body) => {
            vi.spyOn(console, 'warn').mockImplementation(() => undefined);
            vi.spyOn(globalThis, 'fetch').mockResolvedValue(
                new Response(body, {
                    status: 502,
                    statusText: 'Bad Gateway',
                    headers: { 'Content-Type': 'application/json' },
                })
            );
            await expect(managedFetch('/api/test')).rejects.toMatchObject({
                message: 'Bad Gateway (502)',
                status: 502,
            });
        }
    );
});

describe('error contract', () => {
    afterEach(() => vi.restoreAllMocks());

    test('keeps HTTP 401 when its JSON body is malformed', async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => undefined);
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            new Response('{broken', {
                status: 401,
                statusText: 'Unauthorized',
                headers: { 'Content-Type': 'application/json' },
            })
        );
        await expect(managedFetch('/api/test')).rejects.toMatchObject({ status: 401 });
    });

    test('passes through a DOMException cancellation', async () => {
        const error = new DOMException('Aborted', 'AbortError');
        vi.spyOn(globalThis, 'fetch').mockRejectedValue(error);
        await expect(managedFetch('/api/test')).rejects.toBe(error);
    });

    test('preserves structured HTTP errors during normalization', () => {
        const error = {
            status: 403,
            message: 'Denied',
            details: 'Not an owner',
            instanceId: 'p',
            level: 'WARN',
        };
        expect(toRequestError(error)).toEqual(error);
    });

    test.each([
        ['process', () => getLog('p', {})],
        ['segment', () => getSegmentLog('p', 1, {})],
    ])('%s log reports missing or invalid range headers', async (_name, read) => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('log'));
        await expect(read()).rejects.toMatchObject({
            status: 200,
            message: 'Content-Range header is missing (200)',
        });
        vi.mocked(fetch).mockResolvedValueOnce(
            new Response('log', { status: 206, headers: { 'Content-Range': 'invalid' } })
        );
        await expect(read()).rejects.toMatchObject({
            status: 206,
            message: 'Invalid Content-Range header (206)',
            details: 'invalid',
        });
    });

    test('keeps successful log reads unchanged', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            new Response('log', {
                headers: { 'Content-Range': 'bytes 0-3/3' },
            })
        );
        await expect(getSegmentLog('p', 1, {})).resolves.toEqual({
            data: 'log',
            range: { unit: 'bytes', low: 0, high: 3, length: 3 },
        });
    });
});
