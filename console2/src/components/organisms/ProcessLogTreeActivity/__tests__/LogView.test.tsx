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
vi.mock('../logApi', () => ({
    getSegmentLogPage: vi.fn(),
}));
vi.mock('../../../../api/process/log', async (original) => ({
    ...(await original<Record<string, unknown>>()),
    getFullSegmentLog: vi.fn(),
}));
import { ProcessStatus } from '../../../../api/process';
import { PollingError, PollingErrorContext } from '../../../../api/usePolling';
import PollErrorBanner from '../../ProcessActivity/PollErrorBanner';
import { getFullSegmentLog, SegmentStatus } from '../../../../api/process/log';
import { getSegmentLogPage } from '../logApi';
import LogView from '../LogView';
import { buildSegmentTree, SegmentNode } from '../segmentTree';
const entry = (id: number, parentId?: number) => ({
    id,
    parentId,
    name: `step-${id}`,
    createdAt: new Date(1000 + id).toISOString(),
    status: SegmentStatus.OK,
});
const root = (children: ReturnType<typeof entry>[] = []) =>
    buildSegmentTree([entry(1), ...children]).byId.get(1)!;
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
const originalScrollIntoView = Object.getOwnPropertyDescriptor(Element.prototype, 'scrollIntoView');
beforeEach(() => {
    Object.defineProperty(Element.prototype, 'scrollIntoView', {
        configurable: true,
        value: vi.fn(),
    });
    const values = new Map<string, string>();
    vi.stubGlobal('localStorage', {
        getItem: (k: string) => values.get(k) ?? null,
        setItem: (k: string, v: string) => values.set(k, v),
    });
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
    if (originalScrollIntoView) {
        Object.defineProperty(Element.prototype, 'scrollIntoView', originalScrollIntoView);
    } else {
        Reflect.deleteProperty(Element.prototype, 'scrollIntoView');
    }
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('independent log history', () => {
    test('completes an appended partial line and keeps byte anchors and levels', async () => {
        const first = '2026-09-26T01:37:31.071+0000 [ERROR] ошибка';
        const appended = '\r\n\tat run()\n2026-09-26T01:37:32.000+0000 DOCKER: done\n';
        const bytes = (text: string) => new TextEncoder().encode(text).length;
        vi.mocked(getSegmentLogPage)
            .mockResolvedValueOnce(result(first, 0, bytes(first)))
            .mockResolvedValueOnce(result(appended, bytes(first), bytes(first + appended)));
        const selected = root();
        const view = render(viewOf(selected));
        await screen.findByText('ошибка');
        view.rerender(<LogView {...props} node={selected} forceRefresh={true} />);
        await screen.findByText('DOCKER: done');

        const lines = view.container.querySelectorAll('.LogLine');
        expect(lines).toHaveLength(3);
        expect(lines[0].getAttribute('data-anchor')).toBe('l1:0');
        expect(lines[1].getAttribute('data-anchor')).toBe(`l1:${bytes(first + '\r\n')}`);
        expect(lines[1].classList.contains('Level-ERROR')).toBe(true);
        expect(lines[2].classList.contains('Level-ERROR')).toBe(false);
    });
    test('polls the system log until completion and reads its final output', async () => {
        vi.useFakeTimers();
        localStorage.setItem('logTree.logOpts', JSON.stringify({ follow: false }));
        vi.mocked(getSegmentLogPage).mockImplementation(async (_p, _id, query) => {
            if (query.after === undefined) return result('first\n');
            if (query.after === 6) return result('next\n', 6);
            if (query.after === 11) return result('done\n', 11);
            return result('', query.after);
        });
        const selected = buildSegmentTree([
            { id: 0, name: 'system', createdAt: new Date(0).toISOString() },
        ]).byId.get(0)!;
        const view = render(
            <LogView
                {...props}
                node={selected}
                processStatus={ProcessStatus.RUNNING}
                pollingEnabled={true}
            />
        );
        await act(async () => {});
        expect(screen.getByText('first')).toBeTruthy();
        const autoScroll = screen.getByRole('checkbox', {
            name: 'Auto-scroll',
        }) as HTMLInputElement;
        expect(autoScroll.disabled).toBe(false);

        await act(async () => {
            await vi.advanceTimersByTimeAsync(props.dataFetchInterval);
        });
        expect(screen.getByText('next')).toBeTruthy();
        expect(getSegmentLogPage).toHaveBeenCalledTimes(2);

        view.rerender(viewOf(selected));
        await act(async () => {});
        expect(screen.getByText('done')).toBeTruthy();
        expect(autoScroll.disabled).toBe(true);
        const callsAtCompletion = vi.mocked(getSegmentLogPage).mock.calls.length;
        await act(async () => {
            await vi.advanceTimersByTimeAsync(props.dataFetchInterval * 2);
        });
        expect(getSegmentLogPage).toHaveBeenCalledTimes(callsAtCompletion);
    });
    test('shows the latest 100 descendants and reveals history without losing visible logs', async () => {
        const children = Array.from({ length: 101 }, (_, i) => entry(i + 2, 1));
        render(viewOf(root(children)));
        await screen.findByText('output-102');
        expect(screen.queryByText('output-2')).toBeNull();
        expect(vi.mocked(getSegmentLogPage).mock.calls.some((c) => c[1] === 2)).toBe(false);
        fireEvent.click(screen.getByRole('button', { name: 'Load earlier steps' }));
        await screen.findByText('output-2');
        expect(screen.getByText('output-102')).toBeTruthy();
        expect(screen.queryByRole('button', { name: 'Load earlier steps' })).toBeNull();
    });
    test('live metadata appends descendants without discarding displayed history', async () => {
        const children = Array.from({ length: 101 }, (_, i) => entry(i + 2, 1));
        const view = render(viewOf(root(children)));
        await screen.findByText('output-102');
        view.rerender(viewOf(root([...children, entry(103, 1)])));
        await screen.findByText('output-103');
        expect(screen.getByText('output-3')).toBeTruthy();
        expect(screen.queryByText('output-2')).toBeNull();
    });
    test.each([true, false])('limits each segment separately with Follow=%s', async (follow) => {
        localStorage.setItem('logTree.logOpts', JSON.stringify({ follow }));
        const text = Array.from({ length: 500 }, (_, i) => `line-${i + 1}`).join('\n') + '\n';
        vi.mocked(getSegmentLogPage).mockImplementation(async (_p, id) =>
            result(id === 1 ? 'root line\n' : text)
        );
        const view = render(viewOf(root([entry(2, 1)])));
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
    test('a forbidden previous page preserves text and can be retried', async () => {
        vi.mocked(getSegmentLogPage)
            .mockResolvedValueOnce(result('kept\n', 100, 105))
            .mockRejectedValueOnce({
                status: 403,
                message: 'Page failed',
                details: 'retry me',
            })
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

describe('auto-scroll preference and history pause', () => {
    const checkbox = () => screen.getByLabelText('Auto-scroll') as HTMLInputElement;
    const savedFollow = () => JSON.parse(localStorage.getItem('logTree.logOpts')!).follow;
    const liveNode = (id: number) =>
        buildSegmentTree([{ ...entry(id), status: SegmentStatus.RUNNING }]).byId.get(id)!;
    const liveView = (node: SegmentNode, forceRefresh = false) => (
        <LogView
            {...props}
            node={node}
            processStatus={ProcessStatus.RUNNING}
            forceRefresh={forceRefresh}
        />
    );

    test.each(['lines', 'steps'] as const)(
        'loading earlier %s on a completed step does not change the preference',
        async (history) => {
            const text = Array.from({ length: 300 }, (_, i) => `line-${i + 1}`).join('\n') + '\n';
            vi.mocked(getSegmentLogPage).mockImplementation(async (_p, id) =>
                result(history === 'lines' && id === 1 ? text : `output-${id}\n`)
            );
            const selected = root(
                history === 'steps' ? Array.from({ length: 101 }, (_, i) => entry(i + 2, 1)) : []
            );
            const view = render(viewOf(selected));
            await screen.findByText(history === 'lines' ? 'line-300' : 'output-102');
            expect(checkbox().disabled).toBe(true);
            expect(checkbox().checked).toBe(true);
            fireEvent.click(screen.getByRole('button', { name: `Load earlier ${history}` }));
            await screen.findByText(history === 'lines' ? 'line-1' : 'output-2');
            expect(checkbox().checked).toBe(false);
            expect(savedFollow()).toBe(true);

            // Saving another option while paused must not persist the temporary pause.
            fireEvent.click(screen.getByRole('checkbox', { name: 'Wrap' }));
            expect(savedFollow()).toBe(true);
            const next = liveNode(1000);
            view.rerender(liveView(next));
            await screen.findByText('output-1000');
            expect(checkbox().checked).toBe(true);
            expect(checkbox().disabled).toBe(false);
            expect(Element.prototype.scrollIntoView).toHaveBeenCalled();

            view.unmount();
            render(liveView(next));
            await screen.findByText('output-1000');
            expect(checkbox().checked).toBe(true);
        }
    );

    test('pauses live appends while reading history and resumes on explicit enable', async () => {
        const initial = Array.from({ length: 500 }, (_, i) => `line-${i + 1}`).join('\n') + '\n';
        const appended = Array.from({ length: 20 }, (_, i) => `line-${i + 501}`).join('\n') + '\n';
        vi.mocked(getSegmentLogPage)
            .mockResolvedValueOnce(result(initial))
            .mockResolvedValueOnce(result(appended, initial.length));
        const selected = liveNode(1);
        const view = render(liveView(selected));
        await screen.findByText('line-500');
        vi.mocked(Element.prototype.scrollIntoView).mockClear();
        fireEvent.click(screen.getByRole('button', { name: 'Load earlier lines' }));
        expect(checkbox().checked).toBe(false);
        expect(savedFollow()).toBe(true);
        expect(screen.getByText('line-101')).toBeTruthy();

        view.rerender(liveView(selected, true));
        await screen.findByText('line-520');
        expect(screen.getByText('line-101')).toBeTruthy();
        expect(view.container.querySelectorAll('.LogLine')).toHaveLength(420);
        expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled();

        fireEvent.click(checkbox());
        expect(checkbox().checked).toBe(true);
        expect(savedFollow()).toBe(true);
        expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
    });

    test('an explicit disable remains disabled across steps and remounts', async () => {
        const view = render(liveView(liveNode(1)));
        await screen.findByText('output-1');
        fireEvent.click(checkbox());
        expect(savedFollow()).toBe(false);
        vi.mocked(Element.prototype.scrollIntoView).mockClear();
        const next = liveNode(2);
        view.rerender(liveView(next));
        await screen.findByText('output-2');
        expect(checkbox().checked).toBe(false);
        expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled();
        view.unmount();
        render(liveView(next));
        await screen.findByText('output-2');
        expect(checkbox().checked).toBe(false);
        expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled();
    });
});

const PollingPage = ({ node, finished = false }: { node: SegmentNode; finished?: boolean }) => {
    const [errors, setErrors] = React.useState<Map<object, PollingError>>(() => new Map());
    const [refresh, setRefresh] = React.useState(false);
    const report = React.useCallback((id: object, error?: PollingError) => {
        setErrors((previous) => {
            const next = new Map(previous);
            if (error) next.set(id, error);
            else next.delete(id);
            return next;
        });
    }, []);
    return (
        <PollingErrorContext.Provider value={report}>
            <PollErrorBanner
                tabError={[...errors.values()][0]}
                tabLabel="Log Tree"
                retryInterval={1000}
                onRetry={() => setRefresh((value) => !value)}
            />
            <LogView
                {...props}
                node={node}
                dataFetchInterval={1000}
                processStatus={finished ? ProcessStatus.FINISHED : ProcessStatus.RUNNING}
                pollingEnabled={!finished}
                forceRefresh={refresh}
            />
        </PollingErrorContext.Provider>
    );
};

// Exercise the real common poller and page banner around the real log viewer.
describe('shared log polling and page errors', () => {
    const advance = async (ms = 0) => {
        await act(async () => {
            await vi.advanceTimersByTimeAsync(ms);
        });
    };
    const running = (count = 1) =>
        buildSegmentTree(
            Array.from({ length: count }, (_, i) => ({
                ...entry(i + 1, i === 0 ? undefined : 1),
                status: SegmentStatus.RUNNING,
            }))
        ).byId.get(1)!;
    beforeEach(() => {
        vi.useFakeTimers();
        localStorage.setItem('logTree.logOpts', JSON.stringify({ follow: false }));
    });

    test.each([0, 502])(
        'reports one banner for an initial batch failure (%s) and retries it',
        async (status) => {
            let failing = true;
            vi.mocked(getSegmentLogPage).mockImplementation(async (_p, id) => {
                if (failing) throw { status, message: 'Unavailable' };
                return result(`output-${id}\n`);
            });
            const view = render(<PollingPage node={running(6)} />);
            await advance();
            expect(getSegmentLogPage).toHaveBeenCalledTimes(6);
            expect(view.container.querySelectorAll('.ProcessPollError')).toHaveLength(1);
            expect(view.container.querySelectorAll('.LogError')).toHaveLength(0);
            await advance(999);
            expect(getSegmentLogPage).toHaveBeenCalledTimes(6);
            failing = false;
            await advance(1);
            expect(getSegmentLogPage).toHaveBeenCalledTimes(12);
            expect(screen.getByText('output-6')).toBeTruthy();
            expect(view.container.querySelector('.ProcessPollError')).toBeNull();
            expect(
                vi.mocked(getSegmentLogPage).mock.calls.every((call) => call[2].after === undefined)
            ).toBe(true);
        }
    );

    test('retains logs and the shared banner until every transient failure recovers', async () => {
        const failing = new Set<number>();
        vi.mocked(getSegmentLogPage).mockImplementation(async (_p, id, query) => {
            if (failing.has(id)) throw { status: 503, message: 'Unavailable' };
            if (query.after === undefined) return result(`old-${id}\n`);
            return result(query.after === 6 ? `new-${id}\n` : '', query.after);
        });
        const view = render(<PollingPage node={running(3)} />);
        await advance();
        [1, 2, 3].forEach((id) => failing.add(id));
        await advance(1000);
        expect(view.container.querySelectorAll('.ProcessPollError')).toHaveLength(1);
        expect(view.container.querySelectorAll('.LogError')).toHaveLength(0);
        expect(screen.getByText('old-1')).toBeTruthy();
        expect(screen.getByText('old-3')).toBeTruthy();
        failing.delete(1);
        failing.delete(2);
        await advance(1000);
        expect(screen.getByText('new-1')).toBeTruthy();
        expect(view.container.querySelectorAll('.ProcessPollError')).toHaveLength(1);
        failing.clear();
        fireEvent.click(screen.getByRole('button', { name: 'retry now' }));
        await advance();
        expect(screen.getByText('new-3')).toBeTruthy();
        expect(screen.getAllByText('old-1')).toHaveLength(1);
        expect(view.container.querySelector('.ProcessPollError')).toBeNull();
        const third = vi.mocked(getSegmentLogPage).mock.calls.filter((call) => call[1] === 3);
        expect(third.slice(1).map((call) => call[2].after)).toEqual([6, 6, 6]);
    });

    test('keeps retrying the final read after the process finishes, then stops', async () => {
        vi.mocked(getSegmentLogPage)
            .mockResolvedValueOnce(result('old\n'))
            .mockRejectedValueOnce({ status: 502, message: 'Final read failed' })
            .mockResolvedValueOnce(result('done\n', 4));
        const view = render(<PollingPage node={running()} />);
        await advance();
        view.rerender(<PollingPage node={root()} finished={true} />);
        await advance();
        expect(view.container.querySelectorAll('.ProcessPollError')).toHaveLength(1);
        expect(screen.getByText('old')).toBeTruthy();
        await advance(1000);
        expect(screen.getByText('done')).toBeTruthy();
        expect(getSegmentLogPage).toHaveBeenCalledTimes(3);
        expect(
            vi
                .mocked(getSegmentLogPage)
                .mock.calls.slice(1)
                .map((call) => call[2].after)
        ).toEqual([4, 4]);
        expect(view.container.querySelector('.ProcessPollError')).toBeNull();
        await advance(5000);
        expect(getSegmentLogPage).toHaveBeenCalledTimes(3);
    });

    test('retries history in the same direction even on a completed process', async () => {
        vi.mocked(getSegmentLogPage)
            .mockResolvedValueOnce(result('tail\n', 100, 105))
            .mockRejectedValueOnce({ status: 502, message: 'History read failed' })
            .mockResolvedValueOnce(result('prefix\n', 93, 100));
        const view = render(<PollingPage node={root()} finished={true} />);
        await advance();
        fireEvent.click(screen.getByRole('button', { name: 'Load earlier lines' }));
        await advance();
        expect(view.container.querySelectorAll('.ProcessPollError')).toHaveLength(1);
        expect(screen.getByText('tail')).toBeTruthy();
        await advance(1000);
        expect(screen.getByText('prefix')).toBeTruthy();
        expect(
            vi
                .mocked(getSegmentLogPage)
                .mock.calls.slice(1)
                .map((call) => call[2])
        ).toEqual([
            { before: 100, limit: 256 * 1024 },
            { before: 100, limit: 256 * 1024 },
        ]);
        expect(view.container.querySelector('.ProcessPollError')).toBeNull();
        await advance(5000);
        expect(getSegmentLogPage).toHaveBeenCalledTimes(3);
    });

    test.each([403, 'client bug'] as const)(
        'does not retry %s or stop healthy segments',
        async (failure) => {
            let failing = true;
            vi.mocked(getSegmentLogPage).mockImplementation(async (_p, id, query) => {
                if (id === 2 && failing) {
                    if (failure === 'client bug') throw new TypeError('Client bug');
                    throw { status: failure, message: 'Forbidden' };
                }
                return result(`output-${id}\n`, query.after ?? 0);
            });
            const view = render(<PollingPage node={running(2)} />);
            await advance();
            expect(view.container.querySelectorAll('.LogError')).toHaveLength(1);
            expect(view.container.querySelector('.ProcessPollError')).toBeNull();
            await advance(2000);
            const calls = vi.mocked(getSegmentLogPage).mock.calls;
            expect(calls.filter((call) => call[1] === 2)).toHaveLength(1);
            expect(calls.filter((call) => call[1] === 1).length).toBeGreaterThan(1);
            failing = false;
            fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
            await advance();
            expect(screen.getByText('output-2')).toBeTruthy();
            expect(view.container.querySelector('.LogError')).toBeNull();
        }
    );

    test('waits for pending requests without starting overlapping polls', async () => {
        let finish!: (page: ReturnType<typeof result>) => void;
        vi.mocked(getSegmentLogPage).mockImplementationOnce(
            () =>
                new Promise((resolve) => {
                    finish = resolve;
                })
        );
        const view = render(<PollingPage node={running()} />);
        await advance(5000);
        expect(getSegmentLogPage).toHaveBeenCalledTimes(1);
        await act(async () => {
            finish(result('slow\n'));
        });
        await advance(999);
        expect(getSegmentLogPage).toHaveBeenCalledTimes(1);
        await advance(1);
        expect(getSegmentLogPage).toHaveBeenCalledTimes(2);
        view.unmount();
    });

    test('clears the banner on selection change and cancels retries on unmount', async () => {
        vi.mocked(getSegmentLogPage).mockImplementation(async (_p, id) => {
            if (id === 1) throw { status: 502, message: 'Unavailable' };
            return result('other\n');
        });
        const report = vi.fn();
        const renderLog = (selected: SegmentNode) => (
            <PollingErrorContext.Provider value={report}>
                <LogView {...props} node={selected} dataFetchInterval={1000} />
            </PollingErrorContext.Provider>
        );
        const view = render(renderLog(root()));
        await advance();
        expect(report).toHaveBeenLastCalledWith(
            expect.any(Object),
            expect.objectContaining({ retrying: true })
        );
        view.rerender(renderLog(buildSegmentTree([entry(2)]).byId.get(2)!));
        await advance();
        expect(screen.getByText('other')).toBeTruthy();
        expect(report).toHaveBeenLastCalledWith(expect.any(Object), undefined);
        view.rerender(renderLog(root()));
        await advance();
        expect(report).toHaveBeenLastCalledWith(
            expect.any(Object),
            expect.objectContaining({ retrying: true })
        );
        view.unmount();
        const calls = vi.mocked(getSegmentLogPage).mock.calls.length;
        expect(report).toHaveBeenLastCalledWith(expect.any(Object), undefined);
        await advance(5000);
        expect(getSegmentLogPage).toHaveBeenCalledTimes(calls);
    });
});
