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
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('../../../../api/process/event', async (importOriginal) => {
    const actual = await importOriginal<Record<string, unknown>>();
    return { ...actual, listStepEvents: vi.fn() };
});

import { ProcessStatus } from '../../../../api/process';
import {
    listStepEvents,
    ProcessElementEvent,
    ProcessEventEntry,
    ProcessEventType,
} from '../../../../api/process/event';
import { LogSegmentEntry, SegmentStatus } from '../../../../api/process/log';
import StepHeader from '../StepHeader';
import { buildSegmentTree, SegmentNode } from '../segmentTree';

const segment = (
    id: number,
    status: SegmentStatus = SegmentStatus.RUNNING,
    correlationId = `correlation-${id}`
): LogSegmentEntry => ({
    id,
    correlationId,
    name: `step-${id}`,
    createdAt: new Date(1_000 + id).toISOString(),
    status,
});

const node = (
    id: number,
    status: SegmentStatus = SegmentStatus.RUNNING,
    correlationId?: string
): SegmentNode => buildSegmentTree([segment(id, status, correlationId)]).byId.get(id)!;

let eventSequence = 0;
const event = (
    phase: 'pre' | 'post',
    data: Partial<ProcessElementEvent> = {}
): ProcessEventEntry<ProcessElementEvent> => ({
    id: `event-${++eventSequence}`,
    seqId: eventSequence,
    eventType: ProcessEventType.ELEMENT,
    eventDate: new Date(eventSequence).toISOString(),
    data: {
        processDefinitionId: 'main',
        elementId: 'task',
        line: 7,
        column: 3,
        phase,
        ...data,
    },
});

const flush = async () => {
    await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
    });
};

const advance = async (milliseconds: number) => {
    await act(async () => {
        vi.advanceTimersByTime(milliseconds);
        await Promise.resolve();
        await Promise.resolve();
    });
};

const openDetails = async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Step details' }));
    await flush();
};

describe('StepHeader delayed events', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.mocked(listStepEvents).mockReset();
        eventSequence = 0;
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    test('moves from empty to metadata, pre-only input, and delayed post output', async () => {
        const pre = event('pre', { fileName: 'concord.yaml', in: { input: 'current' } });
        const post = event('post', {
            fileName: 'concord.yaml',
            in: { input: 'current' },
            out: { output: 'done' },
        });
        const oldPre = event('pre', { in: { input: 'old' } });
        const oldPost = event('post', { out: { output: 'old' } });
        let basicCalls = 0;
        let detailedCalls = 0;
        vi.mocked(listStepEvents).mockImplementation(async (_instanceId, _step, includeAll) => {
            if (includeAll) {
                detailedCalls += 1;
                return detailedCalls === 1 ? [pre] : [oldPre, oldPost, pre, post];
            }
            basicCalls += 1;
            return basicCalls === 1 ? [] : [pre];
        });

        render(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        expect(vi.mocked(listStepEvents)).toHaveBeenCalledTimes(1);

        await advance(2_000);
        await openDetails();
        expect(screen.getAllByText('main')).toHaveLength(2);
        expect(screen.getByText('concord.yaml:7')).toBeTruthy();
        expect(screen.getByText('input')).toBeTruthy();
        expect(screen.getByText('current')).toBeTruthy();
        expect(screen.getByText('waiting')).toBeTruthy();

        await advance(2_000);
        expect(screen.getByText('output')).toBeTruthy();
        expect(screen.getByText('done')).toBeTruthy();
        expect(screen.queryByText('old')).toBeNull();
    });

    test('starts a fresh completion generation after the running generation exhausts', async () => {
        const pre = event('pre', { in: { input: true } });
        const post = event('post', { in: { input: true }, out: { output: true } });
        let detailedCalls = 0;
        vi.mocked(listStepEvents).mockImplementation(async (_instanceId, _step, includeAll) => {
            if (!includeAll) {
                return [pre];
            }
            detailedCalls += 1;
            return detailedCalls <= 10 ? [pre] : [pre, post];
        });

        const view = render(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        await openDetails();
        for (let attempt = 1; attempt < 10; attempt += 1) {
            await advance(2_000);
        }
        await advance(2_000);

        expect(detailedCalls).toBe(10);
        expect(screen.getByRole('button', { name: 'Refresh details' })).toBeTruthy();

        view.rerender(
            <StepHeader
                instanceId="process"
                node={node(1, SegmentStatus.OK)}
                processStatus={ProcessStatus.FINISHED}
            />
        );
        await flush();

        expect(detailedCalls).toBe(11);
        expect(screen.getByText('output')).toBeTruthy();
        expect(screen.queryByRole('button', { name: 'Refresh details' })).toBeNull();
    });

    test('does not reset attempts for an equivalent replacement run object', async () => {
        vi.mocked(listStepEvents).mockResolvedValue([]);
        const view = render(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        expect(vi.mocked(listStepEvents)).toHaveBeenCalledTimes(1);

        view.rerender(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        expect(vi.mocked(listStepEvents)).toHaveBeenCalledTimes(1);

        await advance(2_000);
        expect(vi.mocked(listStepEvents)).toHaveBeenCalledTimes(2);
    });

    test('opening details and explicit retry each start a request generation', async () => {
        vi.mocked(listStepEvents).mockRejectedValue({
            status: 403,
            message: 'Forbidden (403)',
            details: 'Access denied',
        });
        render(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        expect(screen.getByText('step-1')).toBeTruthy();

        await openDetails();
        expect(vi.mocked(listStepEvents)).toHaveBeenCalledTimes(2);
        expect(screen.getByRole('alert').textContent).toContain('Forbidden (403)');
        expect(screen.getByText('Access denied')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
        await flush();
        expect(vi.mocked(listStepEvents)).toHaveBeenCalledTimes(3);
        expect(screen.getAllByText('step-1')).toHaveLength(2);
    });

    test('shows and retries an initial detailed-event authorization failure', async () => {
        const pre = event('pre');
        let detailedCalls = 0;
        vi.mocked(listStepEvents).mockImplementation(async (_instanceId, _step, includeAll) => {
            if (!includeAll) {
                return [pre];
            }
            detailedCalls += 1;
            throw { status: 403, message: 'Detailed events forbidden (403)' };
        });

        render(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        await openDetails();

        expect(screen.getByRole('alert').textContent).toContain('Detailed events forbidden (403)');
        fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
        await flush();
        expect(detailedCalls).toBe(2);
    });

    test('ignores a response from the previously selected step', async () => {
        let resolveOld!: (events: ProcessEventEntry<ProcessElementEvent>[]) => void;
        const oldResponse = new Promise<ProcessEventEntry<ProcessElementEvent>[]>((resolve) => {
            resolveOld = resolve;
        });
        vi.mocked(listStepEvents).mockImplementation(async (_instanceId, step) => {
            if (step.segmentId === 1) {
                return oldResponse;
            }
            return [event('pre', { processDefinitionId: 'new-flow' })];
        });

        const view = render(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        view.rerender(
            <StepHeader instanceId="process" node={node(2)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        await openDetails();
        expect(screen.getByText('new-flow')).toBeTruthy();

        resolveOld([event('pre', { processDefinitionId: 'old-flow' })]);
        await flush();
        expect(screen.getByText('new-flow')).toBeTruthy();
        expect(screen.queryByText('old-flow')).toBeNull();
    });
});
