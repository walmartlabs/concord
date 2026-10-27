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

import { afterEach, expect, test, vi } from 'vitest';
import { fetchJson, managedFetch, toRequestError } from '../common';
import { getProjectAccess, updateProjectAccess } from '../org/project';
import { get as getTeam, createOrUpdate as createTeam } from '../org/team';

const validation = (messages: string[]) =>
    new Response(JSON.stringify(messages.map((message, id) => ({ id: String(id), message }))), {
        status: 400,
        statusText: 'Bad Request',
        headers: {
            'Content-Type': 'application/vnd.concord-validation-errors-v1+json',
        },
    });
const jsonResponse = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });

afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
});

test.each([
    [
        'project read, organization missing',
        'Organization not found: org',
        () => getProjectAccess('org', 'project'),
    ],
    [
        'project read, project missing',
        'Project not found: project',
        () => getProjectAccess('org', 'project'),
    ],
    [
        'project update',
        'Project not found: project',
        () => updateProjectAccess('org', 'project', []),
    ],
    [
        'team read, organization missing',
        'Organization not found: org',
        () => getTeam('org', 'team'),
    ],
    ['team read, team missing', 'Team not found: team', () => getTeam('org', 'team')],
    ['team create', 'Organization not found: org', () => createTeam('org', { name: 'team' })],
])('%s still retries a server validation response', async (_name, message, request) => {
    vi.useFakeTimers();
    const fetch = vi
        .spyOn(globalThis, 'fetch')
        .mockResolvedValueOnce(validation([message]))
        .mockResolvedValueOnce(jsonResponse({ ok: true }));
    const result = request();
    const assertion = expect(result).resolves.toEqual({ ok: true });
    await vi.advanceTimersByTimeAsync(250);
    await assertion;
    expect(fetch).toHaveBeenCalledTimes(2);
});

test('does not retry unrelated validation errors', async () => {
    const fetch = vi
        .spyOn(globalThis, 'fetch')
        .mockResolvedValueOnce(validation(['Invalid team name']));
    await expect(getTeam('org', 'team')).rejects.toMatchObject({
        status: 400,
        details: 'Invalid team name',
    });
    expect(fetch).toHaveBeenCalledTimes(1);
});

test('preserves every validation message in details', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
        validation(['Invalid name', 'Invalid description'])
    );
    await expect(managedFetch('/api/test')).rejects.toMatchObject({
        status: 400,
        message: 'Bad Request (400)',
        details: 'Invalid name\nInvalid description',
    });
});

test.each([['array'], { field: 'invalid' }, 42])(
    'does not expose non-text details to the renderer: %j',
    async (details) => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
            jsonResponse({ message: 'Invalid value', details }, 400)
        );
        await expect(managedFetch('/api/test')).rejects.toMatchObject({
            status: 400,
            message: 'Invalid value (400)',
            details: undefined,
        });
    }
);

test.each(['<html>Login</html>', '{broken', ''])(
    'keeps the HTTP status when a successful JSON response cannot be parsed: %s',
    async (body) => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(body, { status: 200 }));
        await expect(fetchJson('/api/test')).rejects.toMatchObject({
            status: 200,
            message: 'Invalid JSON response (200)',
        });
    }
);

test('preserves cancellation while reading a successful response body', async () => {
    const response = jsonResponse({ ok: true });
    const error = new DOMException('Aborted', 'AbortError');
    vi.spyOn(response, 'json').mockRejectedValue(error);
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response);
    await expect(fetchJson('/api/test')).rejects.toBe(error);
});

test.each([{ status: 502 }, { status: 502, message: '' }, { status: 502, message: '   ' }])(
    'supplies a heading for a structured error: %j',
    (value) => {
        expect(toRequestError(value)).toMatchObject({
            status: 502,
            message: 'Request failed (502)',
        });
    }
);

test('does not duplicate a network error in details', () => {
    expect(toRequestError(new TypeError('Failed to fetch'))).toEqual({
        status: 0,
        message: 'Failed to fetch (0)',
    });
});

test.each([null, undefined, 42, {}, 'oops'])(
    'normalizes unknown failures with one fallback: %j',
    (value) => {
        expect(toRequestError(value)).toEqual({
            status: 0,
            message: 'Request failed (0)',
        });
    }
);
