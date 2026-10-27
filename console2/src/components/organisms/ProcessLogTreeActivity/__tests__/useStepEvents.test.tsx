import * as React from 'react';
import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { fetchJson } from '../../../../api/common';
import { ProcessStatus } from '../../../../api/process';
import { SegmentKind, SegmentStatus } from '../../../../api/process/log';
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

test.each([false, true])(
    'waits for the selected segment without a correlation fallback (includeAll=%s)',
    async (includeAll) => {
        vi.useFakeTimers();
        const run = {
            id: 20,
            parentId: 19,
            name: 'task: http',
            kind: SegmentKind.STEP,
            correlationId: 'shared-across-iterations',
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
