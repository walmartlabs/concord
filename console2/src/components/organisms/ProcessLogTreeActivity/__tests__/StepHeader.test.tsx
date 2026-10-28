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
    return { ...actual, listSegmentEvents: vi.fn() };
});

import { ProcessStatus } from '../../../../api/process';
import {
    listSegmentEvents,
    ProcessElementEvent,
    ProcessEventEntry,
    ProcessEventType,
} from '../../../../api/process/event';
import { LogSegmentEntry, SegmentKind, SegmentStatus } from '../../../../api/process/log';
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
        vi.mocked(listSegmentEvents).mockReset();
        eventSequence = 0;
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    test('loads metadata and input/output without a correlation ID', async () => {
        const selected = buildSegmentTree([{ ...segment(1), correlationId: undefined }]).byId.get(
            1
        )!;
        vi.mocked(listSegmentEvents).mockResolvedValue([
            event('pre', { fileName: 'without-correlation.yaml', in: { input: 'current' } }),
            event('post', { fileName: 'without-correlation.yaml', out: { output: 'done' } }),
        ]);
        render(
            <StepHeader
                instanceId="process"
                node={selected}
                processStatus={ProcessStatus.RUNNING}
            />
        );
        await flush();
        expect(listSegmentEvents).toHaveBeenCalledWith('process', 1, false);
        await openDetails();
        expect(listSegmentEvents).toHaveBeenCalledWith('process', 1, true);
        expect(screen.getByText('without-correlation.yaml:7')).toBeTruthy();
        expect(screen.getByText('current')).toBeTruthy();
        expect(screen.getByText('done')).toBeTruthy();
    });

    test.each([
        { ...segment(0), kind: SegmentKind.STEP },
        { ...segment(1), kind: SegmentKind.ERROR },
    ])('does not request step events for a system or error segment ($id, $kind)', async (s) => {
        const selected = buildSegmentTree([s]).byId.get(s.id)!;
        render(
            <StepHeader
                instanceId="process"
                node={selected}
                processStatus={ProcessStatus.RUNNING}
            />
        );
        await openDetails();
        await advance(20_000);
        expect(listSegmentEvents).not.toHaveBeenCalled();
        expect(screen.queryByText('Step details have not arrived yet.')).toBeNull();
    });

    test('loads parallel block events without a correlation ID', async () => {
        const selected = buildSegmentTree([
            { ...segment(1), kind: SegmentKind.PARALLEL, correlationId: undefined },
        ]).byId.get(1)!;
        vi.mocked(listSegmentEvents).mockResolvedValue([
            event('post', { fileName: 'parallel.yaml', elementId: 'parallel' }),
        ]);
        render(
            <StepHeader
                instanceId="process"
                node={selected}
                processStatus={ProcessStatus.RUNNING}
            />
        );
        await openDetails();
        expect(listSegmentEvents).toHaveBeenCalledWith('process', 1, false);
        expect(screen.getByText('parallel.yaml:7')).toBeTruthy();
    });

    test.each([
        [SegmentKind.ATTEMPT, 'parent-only', undefined],
        [SegmentKind.ATTEMPT, undefined, 'child-only'],
        [SegmentKind.ITERATION, 'parent-only', undefined],
        [SegmentKind.ITERATION, undefined, 'child-only'],
    ] as const)(
        'resolves a child %s by parentId with correlation IDs %s / %s',
        async (kind, parentCorrelationId, childCorrelationId) => {
            const tree = buildSegmentTree([
                { ...segment(1), correlationId: parentCorrelationId },
                {
                    ...segment(2),
                    parentId: 1,
                    kind,
                    correlationId: childCorrelationId,
                },
            ]);
            vi.mocked(listSegmentEvents).mockResolvedValue([
                event('post', { fileName: 'child.yaml' }),
            ]);
            render(
                <StepHeader
                    instanceId="process"
                    node={tree.byId.get(1)!}
                    processStatus={ProcessStatus.FINISHED}
                />
            );
            await openDetails();
            expect(listSegmentEvents).toHaveBeenCalledWith('process', 2, false);
            expect(screen.getByText('child.yaml:7')).toBeTruthy();
            expect(vi.mocked(listSegmentEvents).mock.calls.every((call) => call[1] === 2)).toBe(true);
        }
    );

    test('resolves retry details from the local tree', async () => {
        const owner = segment(1);
        const run = {
            ...segment(9999, SegmentStatus.OK, owner.correlationId),
            parentId: 1,
            kind: SegmentKind.ATTEMPT,
            attemptIndex: 3,
        };
        const tree = buildSegmentTree([owner, run]);
        vi.mocked(listSegmentEvents).mockResolvedValue([event('post', { fileName: 'local.yaml' })]);
        render(
            <StepHeader
                instanceId="process"
                node={tree.byId.get(1)!}
                processStatus={ProcessStatus.FINISHED}
            />
        );
        await openDetails();
        expect(vi.mocked(listSegmentEvents).mock.calls[0][1]).toBe(9999);
        expect(screen.getByText('local.yaml:7')).toBeTruthy();
    });

    test('moves from empty to metadata, pre-only input, and delayed post output', async () => {
        const pre = event('pre', {
            fileName: 'concord.yaml',
            in: { input: 'current' },
        });
        const post = event('post', {
            fileName: 'concord.yaml',
            in: { input: 'current' },
            out: { output: 'done' },
        });
        const oldPre = event('pre', { in: { input: 'old' } });
        const oldPost = event('post', { out: { output: 'old' } });
        let basicCalls = 0;
        let detailedCalls = 0;
        vi.mocked(listSegmentEvents).mockImplementation(async (_instanceId, _segmentId, includeAll) => {
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
        expect(vi.mocked(listSegmentEvents)).toHaveBeenCalledTimes(1);

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

    test('renders legacy variable mapping arrays', async () => {
        const pre = event('pre', {
            in: [
                {
                    target: 'legacyInput',
                    sourceValue: 'fallback',
                    resolved: 0,
                },
            ],
        });
        const post = event('post', {
            out: [
                {
                    target: 'legacyOutput',
                    sourceValue: true,
                    resolved: false,
                },
            ],
        });
        vi.mocked(listSegmentEvents).mockResolvedValue([pre, post]);

        render(
            <StepHeader
                instanceId="process"
                node={node(1, SegmentStatus.OK)}
                processStatus={ProcessStatus.FINISHED}
            />
        );
        await flush();
        await openDetails();

        expect(screen.getByText('legacyInput')).toBeTruthy();
        expect(screen.getByText('0')).toBeTruthy();
        expect(screen.getByText('legacyOutput')).toBeTruthy();
        expect(screen.getByText('false')).toBeTruthy();
    });

    test('starts a fresh completion generation after the running generation exhausts', async () => {
        const pre = event('pre', { in: { input: true } });
        const post = event('post', { in: { input: true }, out: { output: true } });
        let detailedCalls = 0;
        vi.mocked(listSegmentEvents).mockImplementation(async (_instanceId, _segmentId, includeAll) => {
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
        vi.mocked(listSegmentEvents).mockResolvedValue([]);
        const view = render(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        expect(vi.mocked(listSegmentEvents)).toHaveBeenCalledTimes(1);

        view.rerender(
            <StepHeader instanceId="process" node={node(1)} processStatus={ProcessStatus.RUNNING} />
        );
        await flush();
        expect(vi.mocked(listSegmentEvents)).toHaveBeenCalledTimes(1);

        await advance(2_000);
        expect(vi.mocked(listSegmentEvents)).toHaveBeenCalledTimes(2);
    });

    test('opening details and explicit retry each start a request generation', async () => {
        vi.mocked(listSegmentEvents).mockRejectedValue({
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
        expect(vi.mocked(listSegmentEvents)).toHaveBeenCalledTimes(2);
        expect(screen.getByRole('alert').textContent).toContain('Forbidden (403)');
        expect(screen.getByText('Access denied')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
        await flush();
        expect(vi.mocked(listSegmentEvents)).toHaveBeenCalledTimes(3);
        expect(screen.getAllByText('step-1')).toHaveLength(2);
    });

    test('shows and retries an initial detailed-event authorization failure', async () => {
        const pre = event('pre');
        let detailedCalls = 0;
        vi.mocked(listSegmentEvents).mockImplementation(async (_instanceId, _segmentId, includeAll) => {
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

    test('resolves loop details through a retry iteration to its last attempt', async () => {
        vi.mocked(listSegmentEvents).mockResolvedValue([event('pre')]);
        const tree = buildSegmentTree([
            { ...segment(700), kind: SegmentKind.STEP },
            {
                ...segment(701, SegmentStatus.OK, 'correlation-700'),
                kind: SegmentKind.ITERATION,
                parentId: 700,
                iterationIndex: 0,
            },
            {
                ...segment(702, SegmentStatus.FAILED, 'correlation-700'),
                kind: SegmentKind.ATTEMPT,
                parentId: 701,
                attemptIndex: 0,
            },
            {
                ...segment(703, SegmentStatus.OK, 'correlation-700'),
                kind: SegmentKind.ATTEMPT,
                parentId: 701,
                attemptIndex: 1,
            },
        ]);
        render(
            <StepHeader
                instanceId="process"
                node={tree.byId.get(700)!}
                processStatus={ProcessStatus.FINISHED}
            />
        );
        await flush();
        expect(vi.mocked(listSegmentEvents).mock.calls[0][1]).toBe(703);
    });

    test('ignores a response from the previously selected step', async () => {
        let resolveOld!: (events: ProcessEventEntry<ProcessElementEvent>[]) => void;
        const oldResponse = new Promise<ProcessEventEntry<ProcessElementEvent>[]>((resolve) => {
            resolveOld = resolve;
        });
        vi.mocked(listSegmentEvents).mockImplementation(async (_instanceId, segmentId) => {
            if (segmentId === 1) {
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
