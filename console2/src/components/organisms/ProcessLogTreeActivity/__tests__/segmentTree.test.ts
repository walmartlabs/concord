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

import { SegmentKind, SegmentStatus } from '../../../../api/process/log';
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
    startsCollapsed,
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
            // a failure inside completed segments is handled, here it propagates
            seg(1, undefined, { warnings: 1, status: SegmentStatus.FAILED }),
            seg(2, 1, { errors: 2, status: SegmentStatus.FAILED }),
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
    expect(parseSegmentName('task: http')).toEqual({
        kind: 'task',
        name: 'http',
    });
    expect(parseSegmentName('script: groovy')).toEqual({
        kind: 'script',
        name: 'groovy',
    });
    expect(parseSegmentName('Deploy us-west-2')).toEqual({
        name: 'Deploy us-west-2',
    });
});

test('formatDuration', () => {
    expect(formatDuration(12_345)).toBe('12.3s');
    expect(formatDuration(132_000)).toBe('2m 12s');
    expect(formatDuration(3_720_000)).toBe('1h 02m');
});

const { FAILED, OK } = SegmentStatus;
const { STEP, ITERATION, ATTEMPT, ERROR, PARALLEL } = SegmentKind;
const shape = (nodes: ReturnType<typeof buildSegmentTree>['roots']): unknown =>
    nodes.map((n) =>
        n.children.length > 0 ? { [`${n.kind}:${n.segment.id}`]: shape(n.children) } : n.segment.id
    );

describe('the recorded tree', () => {
    test('is rendered as is, segments of older runners are steps', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { kind: STEP }),
            seg(2, 1, { kind: ITERATION, iterationIndex: 0 }),
            seg(3, 2, { kind: ATTEMPT, attemptIndex: 0 }),
            seg(4, 1, { kind: ERROR }),
            seg(5, 4, { kind: PARALLEL }),
            seg(6),
        ]);
        expect(shape(tree.roots)).toEqual([
            { 'step:1': [{ 'iteration:2': [3] }, { 'error:4': [5] }] },
            6,
        ]);
        expect(tree.byId.size).toBe(6);
    });

    test('a successful retry recovers the failed attempt and its steps', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { kind: STEP, status: OK }),
            seg(2, 1, {
                kind: ATTEMPT,
                attemptIndex: 0,
                name: 'attempt #1',
                status: FAILED,
                errors: 1,
            }),
            seg(3, 2, { kind: STEP, status: FAILED, errors: 1 }),
            seg(4, 2, { kind: STEP, status: OK }),
            seg(5, 1, {
                kind: ATTEMPT,
                attemptIndex: 1,
                name: 'attempt #2',
                status: OK,
            }),
        ]);

        expect(tree.byId.get(2)).toMatchObject({ retried: true });
        expect(tree.byId.get(5)?.retried).toBe(false);
        expect(tree.roots[0].hasFailedDescendant).toBe(true);
        expect(categoryOf(tree.byId.get(2)!)).toBe('FAILED');
        expect(categoryOf(tree.byId.get(3)!)).toBe('FAILED');
        expect(categoryOf(tree.byId.get(4)!)).toBe('OK');
        expect(describeNode(tree.byId.get(2)!)).toEqual({
            name: 'attempt #1',
            note: 'retried',
        });
        expect(pickDefaultSegment(tree, ProcessStatus.FINISHED)?.segment.id).toBe(3);
    });

    test('all unsuccessful attempts remain failed', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { kind: STEP, status: FAILED }),
            seg(2, 1, {
                kind: ATTEMPT,
                attemptIndex: 0,
                name: 'deploy',
                status: FAILED,
            }),
            seg(3, 2, { kind: STEP, status: FAILED }),
            seg(4, 1, {
                kind: ATTEMPT,
                attemptIndex: 1,
                name: 'deploy',
                status: FAILED,
            }),
        ]);
        expect(isFailure(tree.byId.get(2)!)).toBe(true);
        expect(isFailure(tree.byId.get(3)!)).toBe(true);
        expect(isFailure(tree.byId.get(4)!)).toBe(true);
        expect(describeNode(tree.byId.get(2)!)).toEqual({
            name: 'deploy',
            note: 'attempt 1 · retried',
        });
        expect(describeNode(tree.byId.get(4)!)).toEqual({
            name: 'deploy',
            note: 'attempt 2',
        });
        expect(pickDefaultSegment(tree, ProcessStatus.FAILED)!.segment.id).toBe(3);
    });

    test('a failed step stays failed after its error block succeeds', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { kind: STEP, status: FAILED, errors: 1 }),
            seg(2, 1, { kind: STEP, status: FAILED }),
            seg(3, 1, { kind: ERROR, name: 'error' }),
            seg(4, 3, { kind: STEP }),
        ]);
        expect(isFailure(tree.byId.get(1)!)).toBe(true);
        expect(tree.roots[0].hasFailedDescendant).toBe(true);
        expect(categoryOf(tree.byId.get(1)!)).toBe('FAILED');
        expect(describeNode(tree.byId.get(3)!)).toEqual({
            name: 'Error handler',
            kind: 'error handler',
            badgeOnly: true,
        });
    });

    test('a failure inside a failed step stays failed', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { kind: STEP, status: FAILED }),
            seg(2, 1, { kind: PARALLEL, status: FAILED }),
            seg(3, 2, { kind: STEP, status: FAILED }),
        ]);
        expect(isFailure(tree.byId.get(3)!)).toBe(true);
        expect(describeNode(tree.byId.get(2)!)).toEqual({
            name: 'Parallel',
            kind: 'parallel',
            badgeOnly: true,
        });
        expect(pickDefaultSegment(tree, ProcessStatus.FAILED)!.segment.id).toBe(3);
    });

    test('loops start collapsed', () => {
        const tree = buildSegmentTree([
            seg(1, undefined, { kind: STEP, name: 'task: http' }),
            seg(2, 1, { kind: ITERATION, iterationIndex: 0, name: 'deploy a' }),
            seg(3, 1, { kind: ITERATION, iterationIndex: 1, name: 'iteration #2' }),
            seg(4, undefined, { kind: STEP }),
            seg(5, 4, { kind: STEP }),
        ]);
        expect(startsCollapsed(tree.byId.get(1)!)).toBe(true);
        expect(startsCollapsed(tree.byId.get(4)!)).toBe(false);
        expect(describeNode(tree.byId.get(1)!)).toEqual({
            name: 'http',
            kind: 'task',
            note: '2 items',
            loop: true,
        });
        expect(describeNode(tree.byId.get(2)!)).toEqual({
            name: 'deploy a',
            note: 'item #1',
        });
        expect(describeNode(tree.byId.get(3)!)).toEqual({ name: 'item #2' });
    });
});

test('every step belongs to exactly one category', () => {
    const tree = buildSegmentTree([
        seg(0, undefined, { status: undefined, warnings: 2 }),
        seg(1, undefined, { status: SegmentStatus.OK }),
        seg(2, undefined, { status: SegmentStatus.OK, warnings: 1 }),
        seg(3, undefined, { status: SegmentStatus.FAILED, errors: 1 }),
        seg(4, undefined, { kind: STEP, status: SegmentStatus.OK }),
        seg(5, 4, { kind: ATTEMPT, attemptIndex: 0, status: SegmentStatus.FAILED }),
        seg(6, 4, { kind: ATTEMPT, attemptIndex: 1, status: SegmentStatus.OK }),
        seg(7, undefined, { status: SegmentStatus.RUNNING }),
        seg(8, undefined, { status: SegmentStatus.SUSPENDED }),
        seg(9, undefined, { status: SegmentStatus.OK }),
    ]);
    expect(Object.fromEntries(tree.ordered.map((n) => [n.segment.id, categoryOf(n)]))).toEqual({
        0: 'WARNINGS',
        1: 'OK',
        2: 'WARNINGS',
        3: 'FAILED',
        4: 'OK',
        // A later successful attempt does not change the recorded failure.
        5: 'FAILED',
        6: 'OK',
        7: 'RUNNING',
        8: 'RUNNING',
        9: 'OK',
    });
});

test('flattened loop retries retain independent iteration and attempt indices', () => {
    const tree = buildSegmentTree([
        seg(1, undefined, { kind: STEP, status: OK }),
        seg(2, 1, {
            kind: ATTEMPT,
            iterationIndex: 0,
            attemptIndex: 0,
            name: 'Deploy a',
            status: FAILED,
        }),
        seg(3, 1, {
            kind: ATTEMPT,
            iterationIndex: 1,
            attemptIndex: 1,
            name: 'Deploy b',
            status: OK,
        }),
    ]);
    expect(tree.byId.get(2)?.retried).toBe(false);
    expect(describeNode(tree.byId.get(2)!)).toMatchObject({
        name: 'Deploy a',
        note: 'item #1 · attempt 1',
    });
    expect(describeNode(tree.byId.get(1)!)).toMatchObject({ loop: true });
});
