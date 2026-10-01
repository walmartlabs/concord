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

import { describe, expect, test } from 'vitest';

import { SegmentRole, SegmentStatus } from '../../../../api/process/log';
import type { LogSegmentEntry } from '../../../../api/process/log';
import { ProcessStatus } from '../../../../api/process';
import {
    buildSegmentTree,
    categoryOf,
    describeNode,
    formatDuration,
    getAncestors,
    isFailure,
    parseSegmentName,
    pickDefaultSegment,
} from '../segmentTree';

const seg = (
    id: number,
    parentId?: number,
    extra: Partial<LogSegmentEntry> = {}
): LogSegmentEntry => ({
    id,
    parentId,
    name: `segment ${id}`,
    createdAt: '2026-09-25T10:00:00.000Z',
    status: SegmentStatus.OK,
    ...extra,
});

describe('buildSegmentTree', () => {
    test('nests segments by parentId', () => {
        const tree = buildSegmentTree([seg(0), seg(1), seg(2, 1), seg(3, 2), seg(4)]);

        expect(tree.roots.map((n) => n.segment.id)).toEqual([0, 1, 4]);
        expect(tree.byId.get(1)!.children.map((n) => n.segment.id)).toEqual([2]);
        expect(tree.byId.get(3)!.depth).toBe(2);
        expect(getAncestors(tree.byId.get(3)!).map((n) => n.segment.id)).toEqual([1, 2]);
        expect(tree.ordered.map((n) => n.segment.id)).toEqual([0, 1, 2, 3, 4]);
    });

    test('segments without a known parent become roots', () => {
        // old processes (no parentId), unknown parents and invalid references
        const tree = buildSegmentTree([seg(1), seg(2, 42), seg(3, 3), seg(4, 5), seg(5)]);
        expect(tree.roots.map((n) => n.segment.id)).toEqual([1, 2, 3, 4, 5]);
    });

    test('aggregates counters and failures of descendants', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { warnings: 1 }),
            seg(2, 1, { errors: 2 }),
            seg(3, 2, { warnings: 3, status: SegmentStatus.FAILED }),
        ]);

        const root = tree.byId.get(1)!;
        expect(root.totalWarnings).toBe(4);
        expect(root.totalErrors).toBe(2);
        expect(root.hasFailedDescendant).toBe(true);
        expect(tree.byId.get(3)!.hasFailedDescendant).toBe(false);
    });
});

describe('pickDefaultSegment', () => {
    test('prefers the innermost failed segment', () => {
        const tree = buildSegmentTree([
            seg(0, undefined, { status: SegmentStatus.FAILED }),
            seg(1, undefined, { status: SegmentStatus.FAILED }),
            seg(2, 1),
            seg(3, 1, { status: SegmentStatus.FAILED }),
            seg(4, undefined, { status: SegmentStatus.FAILED }),
        ]);
        expect(pickDefaultSegment(tree, ProcessStatus.FAILED)!.segment.id).toBe(3);
    });

    test('follows the latest running segment of a running process', () => {
        const tree = buildSegmentTree([
            seg(0),
            seg(1, undefined, { status: SegmentStatus.RUNNING }),
            seg(2, 1, { status: SegmentStatus.RUNNING }),
        ]);
        expect(pickDefaultSegment(tree, ProcessStatus.RUNNING)!.segment.id).toBe(2);
        expect(pickDefaultSegment(tree, ProcessStatus.FINISHED)!.segment.id).toBe(1);
    });

    test('falls back to the system segment', () => {
        expect(pickDefaultSegment(buildSegmentTree([seg(0)]))!.segment.id).toBe(0);
        expect(pickDefaultSegment(buildSegmentTree([]))).toBeUndefined();
    });
});

test('parseSegmentName splits default names', () => {
    expect(parseSegmentName('task: http')).toEqual({ kind: 'task', name: 'http' });
    expect(parseSegmentName('script: groovy')).toEqual({ kind: 'script', name: 'groovy' });
    expect(parseSegmentName('Deploy us-west-2')).toEqual({ name: 'Deploy us-west-2' });
});

test('formatDuration', () => {
    expect(formatDuration(12_345)).toBe('12.3s');
    expect(formatDuration(132_000)).toBe('2m 12s');
    expect(formatDuration(3_720_000)).toBe('1h 02m');
});

const { FAILED, OK } = SegmentStatus;
const shape = (nodes: ReturnType<typeof buildSegmentTree>['roots']): unknown =>
    nodes.map((n) =>
        n.children.length > 0 ? { [`${n.kind}:${n.segment.id}`]: shape(n.children) } : n.segment.id
    );

describe('groups', () => {
    test('consecutive attempts of a step become a retry group', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { correlationId: 'a', attempt: 1, status: FAILED }),
            seg(2, undefined, { correlationId: 'a', attempt: 2, status: FAILED }),
            seg(3, undefined, { correlationId: 'a', attempt: 3, status: OK }),
            seg(4, undefined, { correlationId: 'b', attempt: 1, status: OK }),
        ]);

        expect(shape(tree.roots)).toEqual([{ 'retry:-11': [1, 2, 3] }, 4]);

        const group = tree.roots[0];
        expect(group.segment.status).toBe(OK);
        expect(describeNode(group).note).toBe('3 attempts · succeeded');
        expect(group.children.map((a) => [describeNode(a).name, !!a.retried])).toEqual([
            ['Attempt 1', true],
            ['Attempt 2', true],
            ['Attempt 3', false],
        ]);
        // retried attempts are not failures
        expect(group.hasFailedDescendant).toBe(false);
        expect(pickDefaultSegment(tree, ProcessStatus.FINISHED)!.segment.id).not.toBe(1);
    });

    test('a successful retry recovers nested failures without relabeling descendants', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, {
                correlationId: 'a',
                attempt: 1,
                status: FAILED,
                errors: 1,
            }),
            seg(2, 1, { status: FAILED, errors: 1 }),
            seg(3, 2, { status: FAILED, errors: 1 }),
            seg(4, 1, { status: OK }),
            seg(5, undefined, { correlationId: 'a', attempt: 2, status: OK }),
        ]);

        expect(tree.byId.get(1)).toMatchObject({ retried: true, recovered: true });
        expect(tree.byId.get(2)).toMatchObject({ recovered: true });
        expect(tree.byId.get(3)).toMatchObject({ recovered: true });
        expect(tree.byId.get(4)).toMatchObject({ recovered: true });
        expect(tree.byId.get(2)?.retried).toBeUndefined();
        expect(tree.roots[0].hasFailedDescendant).toBe(false);
        expect(categoryOf(tree.byId.get(1)!)).toBe('WARNINGS');
        expect(categoryOf(tree.byId.get(2)!)).toBe('WARNINGS');
        expect(categoryOf(tree.byId.get(4)!)).toBe('OK');
        expect(categoryOf(tree.byId.get(5)!)).toBe('OK');
        expect(pickDefaultSegment(tree, ProcessStatus.FINISHED)?.segment.id).toBe(5);
    });

    test('attempts interleaved with other steps (parallel branches)', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { correlationId: 'a', attempt: 1, threadId: 1, status: FAILED }),
            seg(2, undefined, { correlationId: 'b', attempt: 1, threadId: 2, status: FAILED }),
            seg(3, undefined, { correlationId: 'a', attempt: 2, threadId: 1 }),
            seg(4, undefined, { correlationId: 'b', attempt: 2, threadId: 2 }),
            // the same step once more (e.g. its flow is called again)
            seg(5, undefined, { correlationId: 'a', attempt: 1, threadId: 1 }),
        ]);
        expect(shape(tree.roots)).toEqual([{ 'retry:-11': [1, 3] }, { 'retry:-21': [2, 4] }, 5]);
    });

    test('exhausted retries leave only the final attempt failed', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { correlationId: 'a', attempt: 1, status: FAILED }),
            seg(2, undefined, { correlationId: 'a', attempt: 2, status: FAILED }),
        ]);
        expect(tree.roots[0].segment.status).toBe(FAILED);
        expect(describeNode(tree.roots[0]).note).toBe('2 attempts · exhausted');
        expect(tree.byId.get(1)).toMatchObject({ retried: true, recovered: true });
        expect(tree.byId.get(2)?.recovered).toBeFalsy();
        expect(isFailure(tree.byId.get(1)!)).toBe(false);
        expect(isFailure(tree.byId.get(2)!)).toBe(true);
        expect(pickDefaultSegment(tree, ProcessStatus.FAILED)!.segment.id).toBe(2);
    });

    test('late loop iterations keep group identity and update the timeline start', () => {
        const first = buildSegmentTree([
            seg(1, undefined, {
                correlationId: 'a',
                loopIndex: 1,
                createdAt: '2026-01-02T00:00:00Z',
            }),
            seg(3, undefined, {
                correlationId: 'a',
                loopIndex: 2,
                createdAt: '2026-01-03T00:00:00Z',
            }),
            seg(4),
        ]);
        const groupId = first.roots[0].segment.id;

        const second = buildSegmentTree([
            seg(1, undefined, {
                correlationId: 'a',
                loopIndex: 1,
                createdAt: '2026-01-02T00:00:00Z',
            }),
            seg(3, undefined, {
                correlationId: 'a',
                loopIndex: 2,
                createdAt: '2026-01-03T00:00:00Z',
            }),
            seg(2, undefined, {
                correlationId: 'a',
                loopIndex: 0,
                createdAt: '2026-01-01T00:00:00Z',
            }),
            seg(4),
        ]);
        const loop = second.roots[0];

        expect(loop.segment.id).toBe(groupId);
        expect(loop.children.map((child) => child.segment.id)).toEqual([2, 1, 3]);
        expect(loop.segment.createdAt).toBe('2026-01-01T00:00:00Z');
        expect(describeNode(second.byId.get(2)!).note).toBe('item 1');
    });

    test('iterations with several steps and attempts inside', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { correlationId: 'a', loopIndex: 0 }),
            seg(2, undefined, { correlationId: 'b', loopIndex: 0, attempt: 1, status: FAILED }),
            seg(3, undefined, { correlationId: 'b', loopIndex: 0, attempt: 2 }),
            seg(4, undefined, { correlationId: 'a', loopIndex: 1 }),
            seg(5, undefined, { correlationId: 'b', loopIndex: 1, attempt: 1 }),
        ]);
        expect(shape(tree.roots)).toEqual([
            {
                'loop:-12': [
                    { 'iteration:-13': [1, { 'retry:-21': [2, 3] }] },
                    { 'iteration:-43': [4, 5] },
                ],
            },
        ]);
    });

    test('the same loop running again starts a new group', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { correlationId: 'a', loopIndex: 0 }),
            seg(2, undefined, { correlationId: 'a', loopIndex: 1 }),
            seg(3, undefined, { correlationId: 'a', loopIndex: 0 }),
            seg(4, undefined, { correlationId: 'a', loopIndex: 1 }),
            // a different loop right after
            seg(5, undefined, { correlationId: 'c', loopIndex: 0 }),
            seg(6, undefined, { correlationId: 'c', loopIndex: 1 }),
        ]);
        expect(shape(tree.roots)).toEqual([
            { 'loop:-12': [1, 2] },
            { 'loop:-32': [3, 4] },
            { 'loop:-52': [5, 6] },
        ]);
    });

    test('nested groups of the same kind get distinct ids', () => {
        // a named call in a loop, with a loop inside
        const tree = buildSegmentTree([
            seg(1, undefined, { correlationId: 'outer', loopIndex: 0 }),
            seg(2, 1, { correlationId: 'inner', loopIndex: 0 }),
            seg(3, 1, { correlationId: 'inner', loopIndex: 1 }),
            seg(4, undefined, { correlationId: 'outer', loopIndex: 1 }),
        ]);
        const loops = tree.ordered.filter((n) => n.kind === 'loop');
        expect(loops).toHaveLength(2);
        expect(new Set(loops.map((l) => l.segment.id)).size).toBe(2);
        expect(tree.byId.size).toBe(tree.ordered.length);
    });

    test('error handlers are grouped at the end of the failed step', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { status: FAILED }),
            seg(2, 1),
            seg(3, 1, { role: SegmentRole.ERROR_HANDLER }),
            seg(4, 1, { role: SegmentRole.ERROR_HANDLER }),
        ]);
        expect(shape(tree.roots)).toEqual([{ 'segment:1': [2, { 'errorHandler:-34': [3, 4] }] }]);
        expect(describeNode(tree.byId.get(-34)!)).toEqual({ name: 'Error handler' });
    });

    test('retrying and looping error handlers remain inside one handler group', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { status: FAILED }),
            seg(2, 1, {
                role: SegmentRole.ERROR_HANDLER,
                correlationId: 'retry',
                attempt: 1,
                status: FAILED,
            }),
            seg(3, 1, {
                role: SegmentRole.ERROR_HANDLER,
                correlationId: 'retry',
                attempt: 2,
                status: OK,
            }),
            seg(4, 1, {
                role: SegmentRole.ERROR_HANDLER,
                correlationId: 'loop',
                loopIndex: 0,
            }),
            seg(5, 1, {
                role: SegmentRole.ERROR_HANDLER,
                correlationId: 'loop',
                loopIndex: 1,
            }),
        ]);
        const handler = tree.byId.get(1)!.children.find((child) => child.kind === 'errorHandler')!;

        expect(handler.children.map((child) => child.kind)).toEqual(['retry', 'loop']);
        expect(handler.children.flatMap((child) => child.children.map((member) => member.segment.id))).toEqual([
            2, 3, 4, 5,
        ]);
    });
});

test('every step belongs to exactly one category', () => {
    const tree = buildSegmentTree([
        seg(0, undefined, { status: undefined, warnings: 2 }),
        seg(1, undefined, { status: SegmentStatus.OK }),
        seg(2, undefined, { status: SegmentStatus.OK, warnings: 1 }),
        seg(3, undefined, { status: SegmentStatus.FAILED, errors: 1 }),
        seg(4, undefined, { correlationId: 'a', attempt: 1, status: SegmentStatus.FAILED }),
        seg(5, undefined, { correlationId: 'a', attempt: 2, status: SegmentStatus.OK }),
        seg(6, undefined, { status: SegmentStatus.RUNNING }),
        seg(7, undefined, { status: SegmentStatus.SUSPENDED }),
    ]);
    const steps = tree.ordered.filter((n) => n.kind === 'segment');
    expect(Object.fromEntries(steps.map((n) => [n.segment.id, categoryOf(n)]))).toEqual({
        0: 'WARNINGS',
        1: 'OK',
        2: 'WARNINGS',
        3: 'FAILED',
        // a retried attempt had a problem, but the step recovered
        4: 'WARNINGS',
        5: 'OK',
        6: 'RUNNING',
        7: 'RUNNING',
    });
});
