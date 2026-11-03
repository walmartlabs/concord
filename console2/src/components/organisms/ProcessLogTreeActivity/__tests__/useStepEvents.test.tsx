import * as React from 'react';
import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { fetchJson } from '../../../../api/common';
import { ProcessStatus } from '../../../../api/process';
import { LogSegmentEntry, SegmentKind, SegmentStatus } from '../../../../api/process/log';
import { useStepEvents } from '../useStepEvents';

vi.mock('../../../../api/common', async (importOriginal) => ({
    ...(await importOriginal<typeof import('../../../../api/common')>()),
    fetchJson: vi.fn(),
}));

afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.mocked(fetchJson).mockReset();
});

test.each([
    [false, 'shared-across-iterations'],
    [true, 'shared-across-iterations'],
    [false, undefined],
    [true, undefined],
] as const)(
    'waits for the selected segment without a correlation fallback (includeAll=%s, correlationId=%s)',
    async (includeAll, correlationId) => {
        vi.useFakeTimers();
        const run = {
            id: 20,
            parentId: 19,
            name: 'task: http',
            kind: SegmentKind.STEP,
            correlationId,
            status: SegmentStatus.OK,
            createdAt: new Date(0).toISOString(),
        };
        const url = `/api/v1/process/process/event/segment/20?includeAll=${includeAll}`;
        let segmentCalls = 0;
        vi.mocked(fetchJson).mockImplementation(async (requestedUrl) => {
            // An earlier iteration has events under the same correlation ID.
            if (requestedUrl !== url) {
                return [{ data: { phase: 'post', logSegmentId: 10 } }];
            }
            segmentCalls += 1;
            return segmentCalls === 1 ? [] : [{ data: { phase: 'post', logSegmentId: 20 } }];
        });
        const Probe = () => {
            const { events } = useStepEvents('process', run, {
                includeAll,
                enabled: true,
                waitForPost: includeAll,
                processStatus: ProcessStatus.FINISHED,
            });
            return <span>{events?.[0]?.data.logSegmentId ?? 'waiting'}</span>;
        };
        render(<Probe />);
        await act(async () => {});
        expect(screen.getByText('waiting')).toBeTruthy();
        expect(fetchJson).toHaveBeenCalledTimes(1);
        expect(fetchJson).toHaveBeenLastCalledWith(url);

        await act(async () => {
            await vi.advanceTimersByTimeAsync(2_000);
        });
        expect(screen.getByText('20')).toBeTruthy();
        expect(fetchJson).toHaveBeenCalledTimes(2);
        expect(fetchJson).toHaveBeenLastCalledWith(url);
    }
);

const Probe = ({ run, enabled = true }: { run?: LogSegmentEntry; enabled?: boolean }) => {
    const { events } = useStepEvents('process', run, {
        includeAll: false,
        enabled,
        waitForPost: false,
    });
    return <span>{events?.[0]?.data.logSegmentId ?? 'waiting'}</span>;
};

const run: LogSegmentEntry = {
    id: 20,
    name: 'step',
    createdAt: new Date(0).toISOString(),
    status: SegmentStatus.RUNNING,
};

test('waits for both a selected run and enabled loading', async () => {
    vi.mocked(fetchJson).mockResolvedValue([{ data: { logSegmentId: 20 } }]);
    const view = render(<Probe />);
    await act(async () => {});
    expect(fetchJson).not.toHaveBeenCalled();
    view.rerender(<Probe run={run} enabled={false} />);
    await act(async () => {});
    expect(fetchJson).not.toHaveBeenCalled();
    view.rerender(<Probe run={run} />);
    await act(async () => {});
    expect(fetchJson).toHaveBeenCalledTimes(1);
    expect(screen.getByText('20')).toBeTruthy();
});

test('keeps an in-flight response when only segment metadata changes', async () => {
    let resolve!: (events: unknown) => void;
    vi.mocked(fetchJson).mockImplementation(
        () =>
            new Promise((done) => {
                resolve = done;
            })
    );
    const view = render(<Probe run={run} />);
    await act(async () => {});
    view.rerender(
        <Probe
            run={{
                ...run,
                correlationId: 'now-present',
                kind: SegmentKind.ATTEMPT,
                iterationIndex: 2,
                attemptIndex: 1,
            }}
        />
    );
    await act(async () => {});
    expect(fetchJson).toHaveBeenCalledTimes(1);
    await act(async () => {
        resolve([{ data: { logSegmentId: 20 } }]);
    });
    expect(screen.getByText('20')).toBeTruthy();
});
