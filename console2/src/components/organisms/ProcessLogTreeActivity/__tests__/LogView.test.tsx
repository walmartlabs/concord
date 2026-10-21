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
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
vi.mock('../treeApi', () => ({
    getSegmentLogPage: vi.fn(),
    treeApi: { descendants: vi.fn(), nodes: vi.fn() },
}));
vi.mock('../../../../api/process/log', async (original) => ({
    ...(await original<Record<string, unknown>>()),
    getFullSegmentLog: vi.fn(),
}));
import { ProcessStatus } from '../../../../api/process';
import { getFullSegmentLog, SegmentStatus } from '../../../../api/process/log';
import { getSegmentLogPage, treeApi, TreeEntry } from '../treeApi';
import LogView from '../LogView';
import { buildSegmentTree, SegmentNode } from '../segmentTree';
const entry = (id: number, parentId?: number): TreeEntry => ({
    segment: {
        id,
        parentId,
        name: `step-${id}`,
        createdAt: new Date(1000 + id).toISOString(),
        status: SegmentStatus.OK,
    },
    hasChildren: false,
    hasIterations: false,
    retried: false,
    category: 'OK',
});
const root = () =>
    buildSegmentTree(
        [entry(1).segment],
        new Map([[1, { ...entry(1), hasChildren: true }]])
    ).byId.get(1)!;
const result = (text: string, before = 0, after = before + text.length) => ({
    text,
    before,
    after,
    hasPrevious: before > 0,
    hasNext: false,
});
const props = {
    instanceId: 'p',
    processStatus: ProcessStatus.FINISHED,
    dataFetchInterval: 5000,
    forceRefresh: false,
    pollingEnabled: false,
    onSelect: vi.fn(),
};
const viewOf = (node: SegmentNode) => <LogView {...props} node={node} />;
beforeEach(() => {
    const values = new Map<string, string>();
    vi.stubGlobal('localStorage', {
        getItem: (k: string) => values.get(k) ?? null,
        setItem: (k: string, v: string) => values.set(k, v),
    });
    vi.mocked(treeApi.descendants)
        .mockReset()
        .mockResolvedValue({ items: [], ancestors: [entry(1)], next: false });
    vi.mocked(treeApi.nodes).mockReset().mockResolvedValue([]);
    vi.mocked(getSegmentLogPage)
        .mockReset()
        .mockImplementation(async (_p, id) => result(`output-${id}\n`));
    vi.mocked(getFullSegmentLog).mockReset().mockResolvedValue('copy');
    Object.defineProperty(navigator, 'clipboard', {
        configurable: true,
        value: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
});
afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('independent log history', () => {
    test('loads descendants independently of the left tree and does not fetch context ancestors logs', async () => {
        vi.mocked(treeApi.descendants).mockResolvedValue({
            items: [entry(30, 2)],
            ancestors: [entry(1), entry(2, 1)],
            next: true,
            nextId: 30,
        });
        const view = render(viewOf(root()));
        await screen.findByText('output-30');
        expect(screen.queryByText('step-2')).toBeNull();
        expect(screen.getByText('step-30')).toBeTruthy();
        expect(
            vi
                .mocked(getSegmentLogPage)
                .mock.calls.map((c) => c[1])
                .sort((a, b) => a - b)
        ).toEqual([1, 30]);
        const expanded = buildSegmentTree(
            [entry(1).segment, entry(99, 1).segment],
            new Map([[1, entry(1)]])
        ).byId.get(1)!;
        view.rerender(viewOf(expanded));
        expect(screen.getByText('output-30')).toBeTruthy();
        expect(vi.mocked(getSegmentLogPage).mock.calls.some((c) => c[1] === 99)).toBe(false);
        expect(treeApi.descendants).toHaveBeenCalledTimes(1);
    });
    test('loads previous steps without hiding already displayed steps', async () => {
        vi.mocked(treeApi.descendants)
            .mockResolvedValueOnce({
                items: [entry(30, 1)],
                ancestors: [entry(1)],
                next: true,
                nextId: 30,
            })
            .mockResolvedValueOnce({
                items: [entry(20, 1)],
                ancestors: [entry(1)],
                next: false,
                nextId: 20,
            });
        render(viewOf(root()));
        await screen.findByText('output-30');
        fireEvent.click(screen.getByRole('button', { name: 'Load earlier steps' }));
        await screen.findByText('output-20');
        expect(screen.getByText('output-30')).toBeTruthy();
        expect(vi.mocked(treeApi.descendants).mock.calls[1][2].beforeId).toBe(30);
    });
    test.each([true, false])('limits each segment separately with Follow=%s', async (follow) => {
        localStorage.setItem('logTree.logOpts', JSON.stringify({ follow }));
        const text = Array.from({ length: 500 }, (_, i) => `line-${i + 1}`).join('\n') + '\n';
        vi.mocked(treeApi.descendants).mockResolvedValue({
            items: [entry(2, 1)],
            ancestors: [entry(1)],
            next: false,
            nextId: 2,
        });
        vi.mocked(getSegmentLogPage).mockImplementation(async (_p, id) =>
            result(id === 1 ? 'root line\n' : text)
        );
        const view = render(viewOf(root()));
        await screen.findByText('line-500');
        expect(view.container.querySelectorAll('.LogLine')).toHaveLength(201);
        expect(screen.getByText('step-2')).toBeTruthy();
        expect(screen.getByText('root line')).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Load earlier lines' }));
        await waitFor(() => expect(view.container.querySelectorAll('.LogLine')).toHaveLength(401));
        expect(getSegmentLogPage).toHaveBeenCalledTimes(2);
    });
    test('with Follow off, only subsequent appends extend the visible window', async () => {
        localStorage.setItem('logTree.logOpts', JSON.stringify({ follow: false }));
        const initial = Array.from({ length: 300 }, (_, i) => `line-${i + 1}`).join('\n') + '\n';
        const appended = Array.from({ length: 20 }, (_, i) => `line-${i + 301}`).join('\n') + '\n';
        vi.mocked(getSegmentLogPage)
            .mockResolvedValueOnce(result(initial))
            .mockResolvedValueOnce(result(appended, initial.length));
        const selected = root();
        const view = render(viewOf(selected));
        await screen.findByText('line-300');
        expect(view.container.querySelectorAll('.LogLine')).toHaveLength(200);
        expect(screen.queryByText('line-100')).toBeNull();
        expect(screen.getByText('line-101')).toBeTruthy();
        view.rerender(<LogView {...props} node={selected} forceRefresh={true} />);
        await screen.findByText('line-320');
        expect(view.container.querySelectorAll('.LogLine')).toHaveLength(220);
        expect(screen.getByText('line-101')).toBeTruthy();
    });
    test('prepends the previous log page, with no full-log request', async () => {
        vi.mocked(getSegmentLogPage)
            .mockResolvedValueOnce(result('tail\n', 100, 105))
            .mockResolvedValueOnce(result('prefix\n', 93, 100));
        render(viewOf(root()));
        await screen.findByText('tail');
        fireEvent.click(screen.getByRole('button', { name: 'Load earlier lines' }));
        await screen.findByText('prefix');
        expect(screen.getByText('tail')).toBeTruthy();
        expect(vi.mocked(getSegmentLogPage).mock.calls[1][2]).toEqual({
            before: 100,
            limit: 256 * 1024,
        });
        expect(getFullSegmentLog).not.toHaveBeenCalled();
    });
    test('a failed previous page preserves text and can be retried', async () => {
        vi.mocked(getSegmentLogPage)
            .mockResolvedValueOnce(result('kept\n', 100, 105))
            .mockRejectedValueOnce({ status: 500, message: 'Page failed', details: 'retry me' })
            .mockResolvedValueOnce(result('prefix\n', 93, 100));
        render(viewOf(root()));
        await screen.findByText('kept');
        fireEvent.click(screen.getByRole('button', { name: 'Load earlier lines' }));
        await screen.findByText('Page failed');
        expect(screen.getByText('kept')).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
        await screen.findByText('prefix');
    });
    test('copy stays a separate full-log action', async () => {
        render(viewOf(root()));
        await screen.findByText('output-1');
        fireEvent.click(screen.getByRole('button', { name: 'Copy' }));
        await screen.findByRole('button', { name: 'Copied' });
        expect(getFullSegmentLog).toHaveBeenCalledWith('p', 1, 5 * 1024 * 1024);
    });
});

test('log viewer has no level filters, including with saved legacy options', async () => {
    localStorage.setItem(
        'logTree.logOpts',
        JSON.stringify({ level: 'error', wrap: true, follow: false })
    );
    render(viewOf(root()));
    await screen.findByText('output-1');
    expect(screen.queryByText('Warnings')).toBeNull();
    expect(screen.queryByText('Errors')).toBeNull();
    expect(getSegmentLogPage).toHaveBeenCalledWith(
        'p',
        1,
        expect.not.objectContaining({ level: 'error' }),
        expect.any(AbortSignal)
    );
});
