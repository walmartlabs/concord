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
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('../../../../api/process/log', async (importOriginal) => {
    const actual = await importOriginal<Record<string, unknown>>();
    return {
        ...actual,
        getSegmentLog: vi.fn(),
        getFullSegmentLog: vi.fn(),
    };
});

import { ProcessStatus } from '../../../../api/process';
import { getFullSegmentLog, getSegmentLog, SegmentStatus } from '../../../../api/process/log';
import type { LogChunk, LogSegmentEntry } from '../../../../api/process/log';
import LogView from '../LogView';
import { buildSegmentTree } from '../segmentTree';
import type { SegmentNode } from '../segmentTree';

const entry = (id: number, parentId?: number): LogSegmentEntry => ({
    id,
    parentId,
    name: `step-${id}`,
    createdAt: new Date(1_000 + id).toISOString(),
    status: SegmentStatus.OK,
});

const node = (entries: LogSegmentEntry[], id: number): SegmentNode =>
    buildSegmentTree(entries).byId.get(id)!;

const result = (data: string, low = 0, length = data.length): LogChunk => ({
    data,
    range: { unit: 'bytes', low, high: low + data.length, length },
});

const renderView = (selected: SegmentNode, props: Record<string, unknown> = {}) =>
    render(
        <LogView
            instanceId="process"
            node={selected}
            processStatus={ProcessStatus.FINISHED}
            dataFetchInterval={5_000}
            forceRefresh={false}
            pollingEnabled={false}
            onSelect={vi.fn()}
            {...props}
        />
    );

describe('LogView', () => {
    beforeEach(() => {
        const values = new Map<string, string>();
        vi.stubGlobal('localStorage', {
            clear: () => values.clear(),
            getItem: (key: string) => values.get(key) ?? null,
            removeItem: (key: string) => values.delete(key),
            setItem: (key: string, value: string) => values.set(key, value),
        });
        vi.mocked(getSegmentLog).mockReset();
        vi.mocked(getFullSegmentLog).mockReset();
        vi.mocked(getFullSegmentLog).mockResolvedValue('copied');
        Object.defineProperty(navigator, 'clipboard', {
            configurable: true,
            value: { writeText: vi.fn().mockResolvedValue(undefined) },
        });
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        vi.useRealTimers();
    });

    test('reuses same-process cached data when selection changes', async () => {
        const entries = [entry(0), entry(1, 0)];
        const tree = buildSegmentTree(entries);
        vi.mocked(getSegmentLog).mockImplementation(async (_instance, id) =>
            result(id === 0 ? 'root\n' : 'child\n')
        );

        const view = renderView(tree.byId.get(0)!);
        await screen.findByText('root');
        await screen.findByText('child');
        view.rerender(
            <LogView
                instanceId="process"
                node={tree.byId.get(1)!}
                processStatus={ProcessStatus.FINISHED}
                dataFetchInterval={5_000}
                forceRefresh={false}
                pollingEnabled={false}
                onSelect={vi.fn()}
            />
        );
        await screen.findByText('child');

        expect(vi.mocked(getSegmentLog).mock.calls.filter((call) => call[1] === 1)).toHaveLength(1);
    });

    test('retains one bounded loader across list, selection, cadence, suspension, and refresh changes', async () => {
        vi.useFakeTimers();
        Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
            configurable: true,
            value: vi.fn(),
        });

        interface Pending {
            segmentId: number;
            signal?: AbortSignal;
            settled: boolean;
            resolve: (value: LogChunk) => void;
        }
        const calls: Pending[] = [];
        let maxActive = 0;
        vi.mocked(getSegmentLog).mockImplementation(
            (_instance, segmentId, _range, signal) =>
                new Promise<LogChunk>((resolve) => {
                    const call = { segmentId, signal, settled: false, resolve };
                    calls.push(call);
                    maxActive = Math.max(
                        maxActive,
                        calls.filter(
                            (current) => !current.settled && !current.signal?.aborted
                        ).length
                    );
                })
        );

        const running = (id: number, parentId?: number) => ({
            ...entry(id, parentId),
            status: SegmentStatus.RUNNING,
        });
        const makeTree = (grown: boolean) =>
            buildSegmentTree([
                running(0),
                running(1, 0),
                ...[8, 9, 10, 11, 12].map((id) => running(id, 1)),
                entry(13, 1),
                ...(grown ? [running(14, 1)] : []),
            ]);
        const initial = makeTree(false);
        const grown = makeTree(true);
        const common = {
            instanceId: 'process',
            processStatus: ProcessStatus.RUNNING,
            forceRefresh: false,
            pollingEnabled: true,
            onSelect: vi.fn(),
        };

        const view = render(
            <LogView {...common} node={initial.byId.get(0)!} dataFetchInterval={5_000} />
        );
        expect(calls.map((call) => call.segmentId)).toEqual([0, 1, 8, 9]);

        view.rerender(
            <LogView {...common} node={grown.byId.get(0)!} dataFetchInterval={5_000} />
        );
        expect(calls).toHaveLength(4);
        expect(calls.every((call) => !call.signal?.aborted)).toBe(true);

        view.rerender(
            <LogView {...common} node={grown.byId.get(1)!} dataFetchInterval={5_000} />
        );
        expect(calls.filter((call) => call.segmentId === 1)).toHaveLength(1);
        expect(calls.find((call) => call.segmentId === 1)?.signal?.aborted).toBe(false);
        expect(calls.filter((call) => !call.settled && !call.signal?.aborted)).toHaveLength(4);

        view.rerender(
            <LogView {...common} node={grown.byId.get(1)!} dataFetchInterval={1_000} />
        );
        expect(calls.filter((call) => call.segmentId === 1)).toHaveLength(1);

        const settleAll = async () => {
            while (calls.some((call) => !call.settled)) {
                const pending = calls.filter((call) => !call.settled);
                pending.forEach((call) => {
                    call.settled = true;
                    call.resolve(result(`line-${call.segmentId}\n`));
                });
                await act(async () => {
                    await Promise.resolve();
                    await Promise.resolve();
                    await Promise.resolve();
                });
            }
        };
        await settleAll();
        expect(calls.filter((call) => call.segmentId === 13)).toHaveLength(1);

        view.rerender(
            <LogView
                {...common}
                node={grown.byId.get(1)!}
                processStatus={ProcessStatus.SUSPENDED}
                pollingEnabled={false}
                dataFetchInterval={1_000}
            />
        );
        const suspendedCalls = calls.length;
        await act(async () => {
            vi.advanceTimersByTime(5_000);
        });
        expect(calls).toHaveLength(suspendedCalls);

        view.rerender(
            <LogView {...common} node={grown.byId.get(1)!} dataFetchInterval={1_000} />
        );
        await act(async () => {
            vi.advanceTimersByTime(1_000);
        });
        expect(calls.length).toBeGreaterThan(suspendedCalls);
        expect(calls.filter((call) => !call.settled && !call.signal?.aborted)).toHaveLength(4);
        await settleAll();

        view.rerender(
            <LogView
                {...common}
                node={grown.byId.get(1)!}
                dataFetchInterval={1_000}
                forceRefresh={true}
            />
        );
        await settleAll();
        expect(calls.filter((call) => call.segmentId === 13)).toHaveLength(2);
        expect(maxActive).toBe(4);
    });

    test('continues following equal-length tail replacements', async () => {
        vi.useFakeTimers();
        const scrollIntoView = vi.fn();
        Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
            configurable: true,
            value: scrollIntoView,
        });
        vi.mocked(getSegmentLog)
            .mockResolvedValueOnce(result('first\n', 100, 200))
            .mockResolvedValueOnce(result('later\n', 200, 300));
        const selected = node([{ ...entry(0), status: SegmentStatus.RUNNING }], 0);
        renderView(selected, {
            processStatus: ProcessStatus.RUNNING,
            dataFetchInterval: 1_000,
            pollingEnabled: true,
        });
        await act(async () => {
            await Promise.resolve();
            await Promise.resolve();
            await Promise.resolve();
        });
        expect(screen.getByText('first')).toBeTruthy();
        scrollIntoView.mockClear();

        await act(async () => {
            vi.advanceTimersByTime(1_000);
            await Promise.resolve();
            await Promise.resolve();
            await Promise.resolve();
        });

        expect(screen.getByText('later')).toBeTruthy();
        expect(scrollIntoView).toHaveBeenCalled();
    });

    test('returns to the bounded tail after a one-shot full load', async () => {
        vi.useFakeTimers();
        vi.mocked(getSegmentLog)
            .mockResolvedValueOnce(result('tail\n', 100, 200))
            .mockResolvedValueOnce(result('prefix\ntail\n', 0, 200))
            .mockResolvedValueOnce(result('tail\nnew\n', 100, 209));
        const selected = node([{ ...entry(0), status: SegmentStatus.RUNNING }], 0);
        renderView(selected, {
            processStatus: ProcessStatus.RUNNING,
            dataFetchInterval: 1_000,
            pollingEnabled: true,
        });
        await act(async () => {
            await Promise.resolve();
            await Promise.resolve();
            await Promise.resolve();
        });
        expect(screen.getByText('tail')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', { name: 'Load full log' }));
        await act(async () => {
            await Promise.resolve();
            await Promise.resolve();
            await Promise.resolve();
        });
        expect(screen.getByText('prefix')).toBeTruthy();

        await act(async () => {
            vi.advanceTimersByTime(1_000);
            await Promise.resolve();
            await Promise.resolve();
            await Promise.resolve();
        });

        expect(screen.queryByText('prefix')).toBeNull();
        expect(screen.getByText('new')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Load full log' })).toBeTruthy();
    });

    test('clears completed full requests for nested live segments', async () => {
        vi.useFakeTimers();
        const childRanges: Array<{ low?: number; high?: number }> = [];
        const rangedResult = (
            data: string,
            low: number,
            high: number,
            length: number
        ): LogChunk => ({
            data,
            range: { unit: 'bytes', low, high, length },
        });
        vi.mocked(getSegmentLog).mockImplementation(async (_instance, id, range) => {
            if (id === 0) {
                return result('root\n');
            }
            childRanges.push(range);
            if (childRanges.length === 1) {
                return rangedResult('tail\n', 100_000, 200_000, 200_000);
            }
            if (childRanges.length === 2) {
                return rangedResult('prefix\ntail\n', 0, 200_000, 200_000);
            }
            return rangedResult('tail\nnew\n', 150_000, 200_010, 200_010);
        });
        const entries = [
            { ...entry(0), status: SegmentStatus.RUNNING },
            { ...entry(1, 0), status: SegmentStatus.RUNNING },
        ];
        renderView(node(entries, 0), {
            processStatus: ProcessStatus.RUNNING,
            dataFetchInterval: 1_000,
            pollingEnabled: true,
        });
        await act(async () => {
            await Promise.resolve();
            await Promise.resolve();
            await Promise.resolve();
        });

        fireEvent.click(screen.getByRole('button', { name: 'load full' }));
        await act(async () => {
            await Promise.resolve();
            await Promise.resolve();
            await Promise.resolve();
        });
        expect(screen.getByText('prefix')).toBeTruthy();

        await act(async () => {
            vi.advanceTimersByTime(1_000);
            await Promise.resolve();
            await Promise.resolve();
            await Promise.resolve();
        });

        expect(screen.getByText('new')).toBeTruthy();
        expect(childRanges).toHaveLength(3);
        expect(childRanges[1]).toEqual({ low: 0 });
        expect(childRanges[2].low).toBeGreaterThan(0);
    });

    test('caps rendered rows at 2,000 and discloses earlier entries', async () => {
        const lines = Array.from({ length: 2_500 }, (_value, index) => `line-${index + 1}`).join('\n');
        vi.mocked(getSegmentLog).mockResolvedValue(result(`${lines}\n`));
        const view = renderView(node([entry(0)], 0));

        await waitFor(() => expect(view.container.querySelectorAll('.LogLine')).toHaveLength(2_000));
        expect(screen.getByText('line-2500')).toBeTruthy();
        expect(screen.queryByText('line-1')).toBeNull();

        fireEvent.click(screen.getByRole('button', { name: 'Show up to 2,000 earlier log entries' }));
        await waitFor(() => expect(view.container.querySelectorAll('.LogLine')).toHaveLength(2_500));
        expect(screen.getByText('line-1')).toBeTruthy();
    });

    test('bounds nested requests and reveals another budget page explicitly', async () => {
        const entries = [entry(0), ...Array.from({ length: 40 }, (_value, i) => entry(i + 1, 0))];
        vi.mocked(getSegmentLog).mockImplementation(async (_instance, id) => result(`line-${id}\n`));
        renderView(node(entries, 0));

        await waitFor(() => expect(vi.mocked(getSegmentLog)).toHaveBeenCalledTimes(29));
        expect(screen.getByRole('button', { name: 'Show more nested steps' })).toBeTruthy();

        fireEvent.click(screen.getByRole('button', { name: 'Show more nested steps' }));
        await waitFor(() => expect(vi.mocked(getSegmentLog)).toHaveBeenCalledTimes(41));
    });

    test('keeps a child ownership header inside the 2,000-item cap', async () => {
        const entries = [entry(0), entry(1, 0)];
        const childLines = Array.from({ length: 2_500 }, (_value, index) => `child-${index + 1}`).join(
            '\n'
        );
        vi.mocked(getSegmentLog).mockImplementation(async (_instance, id) =>
            result(id === 0 ? '' : `${childLines}\n`)
        );
        const view = renderView(node(entries, 0));

        await waitFor(() => {
            const rows = view.container.querySelectorAll('.LogLine, .SegmentLinkRow');
            expect(rows).toHaveLength(2_000);
        });
        expect(view.container.querySelector('.SegmentLinkRow .Name')?.textContent).toBe('step-1');
        expect(view.container.querySelectorAll('.LogLine')).toHaveLength(1_999);
    });

    test('shows structured poll errors and retries without removing existing text', async () => {
        vi.mocked(getSegmentLog)
            .mockResolvedValueOnce(result('kept line\n'))
            .mockRejectedValueOnce({
                status: 401,
                message: 'Authentication required (401)',
                details: 'Session expired',
            })
            .mockResolvedValueOnce(result('kept line\nnew line\n'));
        const selected = node([entry(0)], 0);
        const view = renderView(selected);
        await screen.findByText('kept line');

        view.rerender(
            <LogView
                instanceId="process"
                node={selected}
                processStatus={ProcessStatus.FINISHED}
                dataFetchInterval={5_000}
                forceRefresh={true}
                pollingEnabled={false}
                onSelect={vi.fn()}
            />
        );
        expect(await screen.findByText('Authentication required (401)')).toBeTruthy();
        expect(screen.getByText('Session expired')).toBeTruthy();
        expect(screen.getByText('kept line')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
        expect(await screen.findByText('new line')).toBeTruthy();
    });

    test('switching instances resets disclosure and ignores delayed copy completion', async () => {
        let resolveCopy!: (value: string) => void;
        const copy = new Promise<string>((resolve) => {
            resolveCopy = resolve;
        });
        vi.mocked(getFullSegmentLog).mockReturnValueOnce(copy);
        vi.mocked(getSegmentLog).mockImplementation(async (instanceId) =>
            instanceId === 'first' ? result('old\n', 100, 200) : result('new\n')
        );
        const selected = node([entry(0)], 0);
        const view = renderView(selected, { instanceId: 'first' });
        await screen.findByText('old');
        fireEvent.click(screen.getByRole('button', { name: 'Load full log' }));
        fireEvent.click(screen.getByRole('button', { name: 'Copy' }));
        expect(screen.getByRole('button', { name: 'Copying...' })).toBeTruthy();

        view.rerender(
            <LogView
                instanceId="second"
                node={selected}
                processStatus={ProcessStatus.FINISHED}
                dataFetchInterval={5_000}
                forceRefresh={false}
                pollingEnabled={false}
                onSelect={vi.fn()}
            />
        );
        expect(await screen.findByText('new')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Copy' })).toBeTruthy();
        expect(screen.queryByRole('button', { name: 'Load full log' })).toBeNull();

        resolveCopy('old copy');
        await Promise.resolve();
        expect(screen.getByRole('button', { name: 'Copy' })).toBeTruthy();
    });
});
