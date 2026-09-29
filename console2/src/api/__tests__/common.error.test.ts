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

import { managedFetch } from '../common';

describe('managedFetch errors', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    test('preserves a JSON error message, status, and details', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            new Response(JSON.stringify({ message: 'Authentication required', details: 'Session expired' }), {
                status: 401,
                statusText: 'Unauthorized',
                headers: { 'Content-Type': 'application/json' },
            })
        );

        await expect(managedFetch('/api/test')).rejects.toMatchObject({
            message: 'Authentication required (401)',
            details: 'Session expired',
            status: 401,
        });
    });

    test('uses plain text as the visible message and preserves it as details', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            new Response('Access denied', {
                status: 403,
                statusText: 'Forbidden',
                headers: { 'Content-Type': 'text/plain' },
            })
        );

        await expect(managedFetch('/api/test')).rejects.toEqual({
            message: 'Access denied (403)',
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
            details: 'Failed to fetch',
            status: 0,
        });
    });

    test('preserves an Error named AbortError', async () => {
        const error = new Error('The operation was aborted');
        error.name = 'AbortError';
        vi.spyOn(globalThis, 'fetch').mockRejectedValue(error);

        await expect(managedFetch('/api/test')).rejects.toBe(error);
    });
});
