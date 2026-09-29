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

import { parseISO as parseDate } from 'date-fns';

import { LogSegmentEntry, SegmentRole, SegmentStatus } from '../../../api/process/log';
import { isFinal, ProcessStatus } from '../../../api/process';

export const SYSTEM_SEGMENT_ID = 0;

/**
 * "segment" nodes are the real log segments, the rest are groups built on the client:
 * - retry: consecutive attempts of a step with "retry";
 * - loop: iterations of a loop;
 * - iteration: steps of a single loop iteration (when there's more than one);
 * - errorHandler: steps of the "error" block of the failed parent step.
 */
export type NodeKind = 'segment' | 'retry' | 'loop' | 'iteration' | 'errorHandler';

export interface SegmentNode {
    kind: NodeKind;
    // a synthetic entry (negative id) for groups
    segment: LogSegmentEntry;
    // overrides the segment name, e.g. "Attempt 2"
    label?: string;
    // a failed attempt that was retried: not a failure of the process
    retried?: boolean;
    // a failed attempt (including its descendants) that a later attempt recovered
    recovered?: boolean;
    // number of attempts/iterations in a group
    size?: number;
    parent?: SegmentNode;
    children: SegmentNode[];
    depth: number;
    // counters of the segment and all its descendants
    totalWarnings: number;
    totalErrors: number;
    hasFailedDescendant: boolean;
}

export interface SegmentTree {
    roots: SegmentNode[];
    byId: Map<number, SegmentNode>;
    // all nodes in the tree (pre-)order
    ordered: SegmentNode[];
}

export const isGroup = (n: SegmentNode) => n.kind !== 'segment';

// a real failure: failed and not recovered by a later attempt
export const isFailure = (node: SegmentNode) =>
    node.segment.status === SegmentStatus.FAILED && !node.recovered;

const newNode = (kind: NodeKind, segment: LogSegmentEntry): SegmentNode => ({
    kind,
    segment,
    children: [],
    depth: 0,
    totalWarnings: 0,
    totalErrors: 0,
    hasFailedDescendant: false,
});

// stable ids of the groups: derived from the first member, so they survive the polling
const GROUP_ID_OFFSET: Record<NodeKind, number> = {
    segment: 0,
    retry: 1,
    loop: 2,
    iteration: 3,
    errorHandler: 4,
};
const firstSegmentId = (n: SegmentNode): number =>
    n.kind === 'segment' ? n.segment.id : firstSegmentId(n.children[0]);
const groupId = (kind: NodeKind, first: SegmentNode) =>
    -(firstSegmentId(first) * 10 + GROUP_ID_OFFSET[kind]);
// nested groups of the same kind (e.g. a loop in a loop) start with the same segment
const GROUP_ID_COLLISION_STEP = 1_000_000_000_000;

const aggregateStatus = (members: SegmentNode[]): SegmentStatus | undefined => {
    const statuses = members.filter((member) => !member.recovered).map((member) => member.segment.status);
    if (statuses.includes(SegmentStatus.RUNNING)) {
        return SegmentStatus.RUNNING;
    }
    if (statuses.includes(SegmentStatus.SUSPENDED)) {
        return SegmentStatus.SUSPENDED;
    }
    if (statuses.includes(SegmentStatus.FAILED)) {
        return SegmentStatus.FAILED;
    }
    return statuses.every((status) => status === SegmentStatus.OK)
        ? SegmentStatus.OK
        : undefined;
};

const latestUpdate = (members: SegmentNode[]): string | undefined => {
    let result: string | undefined;
    for (const m of members) {
        const t = m.segment.statusUpdatedAt;
        if (t === undefined) {
            return undefined;
        }
        if (result === undefined || parseDate(t).getTime() > parseDate(result).getTime()) {
            result = t;
        }
    }
    return result;
};

// "deploy us-east-1", "deploy us-west-2" -> "deploy"
const commonName = (names: string[]): string => {
    if (names.every((n) => n === names[0])) {
        return names[0];
    }
    let prefix = names[0];
    for (const n of names) {
        while (!n.startsWith(prefix)) {
            prefix = prefix.substring(0, prefix.length - 1);
        }
    }
    prefix = prefix.replace(/[\s:#'"(\[{-]+$/, '');
    return prefix.length >= 3 ? prefix : names[0];
};

const makeGroup = (
    kind: NodeKind,
    identityAnchor: SegmentNode,
    members: SegmentNode[],
    name: string,
    status: SegmentStatus | undefined,
    extra: Partial<LogSegmentEntry> = {}
): SegmentNode => {
    const createdAt = members.reduce(
        (earliest, member) =>
            parseDate(member.segment.createdAt).getTime() < parseDate(earliest).getTime()
                ? member.segment.createdAt
                : earliest,
        members[0].segment.createdAt
    );
    const group = newNode(kind, {
        id: groupId(kind, identityAnchor),
        name,
        createdAt,
        status,
        statusUpdatedAt: latestUpdate(members),
        ...extra,
    });
    group.size = members.length;
    group.children = members;
    return group;
};

const displayNameOf = (n: SegmentNode) => n.label ?? parseSegmentName(n.segment.name).name;

const markRecovered = (node: SegmentNode) => {
    node.recovered = true;
    node.children.forEach(markRecovered);
};

// attempts of the same step. They are not always adjacent: segments of other threads
// (e.g. parallel branches with "retry") can be created in between
const groupRetries = (nodes: SegmentNode[]): SegmentNode[] => {
    const runs: SegmentNode[][] = [];
    const open = new Map<string, SegmentNode[]>();
    const runOf = new Map<SegmentNode, SegmentNode[]>();

    for (const n of nodes) {
        const s = n.segment;
        if (n.kind !== 'segment' || s.attempt === undefined || !s.correlationId) {
            continue;
        }
        const key = `${s.correlationId}|${s.loopIndex}|${s.threadId}`;
        let run = open.get(key);
        const last = run?.[run.length - 1];
        // attempts go up, a lower number means the step runs again
        if (!run || (last?.segment.attempt ?? 0) >= s.attempt) {
            run = [];
            runs.push(run);
            open.set(key, run);
        }
        run.push(n);
        runOf.set(n, run);
    }

    const result: SegmentNode[] = [];
    for (const n of nodes) {
        const run = runOf.get(n);
        if (!run || run.length < 2) {
            result.push(n);
            continue;
        }
        if (run[0] !== n) {
            // already added as a part of the group
            continue;
        }

        run.forEach((attempt, idx) => {
            attempt.label = `Attempt ${attempt.segment.attempt}`;
            attempt.retried =
                idx < run.length - 1 && attempt.segment.status === SegmentStatus.FAILED;
            if (attempt.retried) {
                markRecovered(attempt);
            }
        });
        const first = run[0].segment;
        const last = run[run.length - 1].segment;
        result.push(
            makeGroup('retry', run[0], run, parseSegmentName(first.name).name, last.status, {
                correlationId: first.correlationId,
                loopIndex: first.loopIndex,
                threadId: first.threadId,
            })
        );
    }
    return result;
};

const loopIndexOf = (n: SegmentNode) => n.segment.loopIndex;

// consecutive segments of loop iterations. A new loop starts when a step that wasn't seen in
// the loop yet shows up after the first iteration (the steps of an iteration appear in its first run)
const groupLoops = (nodes: SegmentNode[]): SegmentNode[] => {
    const result: SegmentNode[] = [];
    let i = 0;
    while (i < nodes.length) {
        const n = nodes[i];
        if (loopIndexOf(n) === undefined) {
            result.push(n);
            i++;
            continue;
        }

        const steps = new Set<string | undefined>([n.segment.correlationId]);
        const seen = new Set<string>([`${n.segment.correlationId}#${loopIndexOf(n)}`]);
        let maxIndex = loopIndexOf(n)!;
        let j = i + 1;
        while (j < nodes.length) {
            const idx = loopIndexOf(nodes[j]);
            if (idx === undefined) {
                break;
            }
            const step = nodes[j].segment.correlationId;
            const key = `${step}#${idx}`;
            // the same iteration of the same step again: the loop runs once more
            if (seen.has(key) || (!steps.has(step) && idx < maxIndex)) {
                break;
            }
            steps.add(step);
            seen.add(key);
            maxIndex = Math.max(maxIndex, idx);
            j++;
        }

        const members = nodes.slice(i, j);
        const byIndex = new Map<number, SegmentNode[]>();
        members.forEach((m) => {
            const idx = loopIndexOf(m)!;
            byIndex.set(idx, [...(byIndex.get(idx) ?? []), m]);
        });

        if (byIndex.size < 2) {
            result.push(...members);
            i = j;
            continue;
        }

        const indexes = [...byIndex.keys()].sort((a, b) => a - b);
        const single = indexes.every((idx) => byIndex.get(idx)!.length === 1);
        const iterations = indexes.map((idx) => {
            const items = byIndex.get(idx)!;
            if (single) {
                return items[0];
            }
            const it = makeGroup(
                'iteration',
                items[0],
                items,
                `Iteration #${idx + 1}`,
                aggregateStatus(items),
                {
                    loopIndex: idx,
                }
            );
            it.label = `Iteration #${idx + 1}`;
            return it;
        });

        result.push(
            makeGroup(
                'loop',
                members[0],
                iterations,
                commonName(members.map(displayNameOf)),
                aggregateStatus(iterations)
            )
        );
        i = j;
    }
    return result;
};

// steps of the "error" block go into a group at the end of the failed step
const groupChildren = (nodes: SegmentNode[]): SegmentNode[] => {
    nodes.forEach((node) => {
        node.children = groupChildren(node.children);
    });

    const ordinary = nodes.filter((node) => node.segment.role !== SegmentRole.ERROR_HANDLER);
    const handlers = nodes.filter((node) => node.segment.role === SegmentRole.ERROR_HANDLER);
    const grouped = groupLoops(groupRetries(ordinary));
    if (handlers.length === 0) {
        return grouped;
    }

    const groupedHandlers = groupLoops(groupRetries(handlers));
    const group = makeGroup(
        'errorHandler',
        handlers[0],
        groupedHandlers,
        'Error handler',
        aggregateStatus(groupedHandlers)
    );
    group.label = 'Error handler';
    return [...grouped, group];
};

export const buildSegmentTree = (segments: LogSegmentEntry[]): SegmentTree => {
    const nodes = new Map<number, SegmentNode>();
    segments.forEach((s) => nodes.set(s.id, newNode('segment', s)));

    const roots: SegmentNode[] = [];
    segments.forEach((s) => {
        const node = nodes.get(s.id)!;
        // parents are always created before their children, which also protects from cycles
        const parent =
            s.parentId !== undefined && s.parentId < s.id ? nodes.get(s.parentId) : undefined;
        if (parent) {
            parent.children.push(node);
        } else {
            roots.push(node);
        }
    });

    const groupedRoots = groupChildren(roots);

    const byId = new Map<number, SegmentNode>();
    const ordered: SegmentNode[] = [];
    const walk = (node: SegmentNode, parent: SegmentNode | undefined, depth: number) => {
        node.parent = parent;
        node.depth = depth;
        // the outer group keeps the id, the inner ones are shifted: stable between the polls
        while (byId.has(node.segment.id)) {
            node.segment.id -= GROUP_ID_COLLISION_STEP;
        }
        byId.set(node.segment.id, node);
        ordered.push(node);

        let warnings = node.segment.warnings ?? 0;
        let errors = node.segment.errors ?? 0;
        let failed = false;
        node.children.forEach((c) => {
            walk(c, node, depth + 1);
            warnings += c.totalWarnings;
            errors += c.totalErrors;
            failed = failed || c.hasFailedDescendant || isFailure(c);
        });

        node.totalWarnings = warnings;
        node.totalErrors = errors;
        node.hasFailedDescendant = failed;
    };
    groupedRoots.forEach((r) => walk(r, undefined, 0));

    return { roots: groupedRoots, byId, ordered };
};

export const getAncestors = (node: SegmentNode): SegmentNode[] => {
    const result: SegmentNode[] = [];
    let p = node.parent;
    while (p) {
        result.unshift(p);
        p = p.parent;
    }
    return result;
};

/**
 * The segment to show when the user opens the log without an explicit selection:
 * the first failed one (the innermost, where the error actually happened), otherwise
 * the most recent running one, otherwise the first one.
 */
export const pickDefaultSegment = (
    tree: SegmentTree,
    processStatus?: ProcessStatus
): SegmentNode | undefined => {
    const steps = tree.ordered.filter(
        (n) => n.kind === 'segment' && n.segment.id !== SYSTEM_SEGMENT_ID
    );

    const failed = steps.find((n) => isFailure(n) && !n.hasFailedDescendant);
    if (failed) {
        return failed;
    }

    if (!isFinal(processStatus)) {
        const running = steps.filter((n) => n.segment.status === SegmentStatus.RUNNING);
        if (running.length > 0) {
            return running[running.length - 1];
        }
    }

    return steps.find((n) => !n.recovered) ?? tree.ordered[0];
};

export type StepCategory = 'FAILED' | 'RUNNING' | 'WARNINGS' | 'OK';

/**
 * Every step belongs to exactly one category, so the counters of the filters add up to "All".
 */
export const categoryOf = (n: SegmentNode): StepCategory => {
    const status = n.segment.status;
    if (isFailure(n)) {
        return 'FAILED';
    }
    if (status === SegmentStatus.RUNNING || status === SegmentStatus.SUSPENDED) {
        return 'RUNNING';
    }
    // A recovered failure is historical evidence, but a successful sibling that
    // shares the recovered subtree remains clean.
    if (
        (n.recovered && status === SegmentStatus.FAILED) ||
        (n.segment.warnings ?? 0) > 0 ||
        (n.segment.errors ?? 0) > 0
    ) {
        return 'WARNINGS';
    }
    return 'OK';
};

export interface NodeDescription {
    name: string;
    // a badge, e.g. "task" or "retry"
    kind?: string;
    // a short secondary text, e.g. "3 attempts · exhausted"
    note?: string;
}

export const describeNode = (n: SegmentNode): NodeDescription => {
    const status = n.segment.status;
    switch (n.kind) {
        case 'retry': {
            const outcome =
                status === SegmentStatus.FAILED
                    ? ' · exhausted'
                    : status === SegmentStatus.OK
                    ? ' · succeeded'
                    : '';
            return { name: n.segment.name, note: `${n.size} attempts${outcome}` };
        }
        case 'loop':
            return {
                name: n.segment.name,
                kind: 'loop',
                note: `${n.size} ${n.size === 1 ? 'item' : 'items'}`,
            };
        case 'iteration':
            return { name: n.label ?? n.segment.name };
        case 'errorHandler':
            return { name: 'Error handler' };
    }

    if (n.segment.id === SYSTEM_SEGMENT_ID) {
        return { name: 'System', kind: 'system' };
    }

    // an attempt of a retry group
    if (n.label) {
        return { name: n.label, note: n.retried ? 'retried' : undefined };
    }

    const { kind, name } = parseSegmentName(n.segment.name);
    // a single-step iteration of a loop
    const note =
        n.parent?.kind === 'loop' && n.segment.loopIndex !== undefined
            ? `item ${n.segment.loopIndex + 1}`
            : undefined;
    return { name, kind, note };
};

const KIND_RE = /^(task|script): (.+)$/;

/**
 * Splits default segment names (e.g. "task: http") into the kind and the name.
 */
export const parseSegmentName = (name: string): { kind?: string; name: string } => {
    const m = KIND_RE.exec(name);
    if (!m) {
        return { name };
    }
    return { kind: m[1], name: m[2] };
};

export interface SegmentTiming {
    start: number;
    end: number;
    // false for segments that never report their completion (e.g. the system segment)
    known: boolean;
}

export const getSegmentTiming = (
    segment: LogSegmentEntry,
    processStatus: ProcessStatus | undefined,
    now: number
): SegmentTiming => {
    const start = parseDate(segment.createdAt).getTime();

    if (segment.status === SegmentStatus.RUNNING && !isFinal(processStatus)) {
        return { start, end: Math.max(start, now), known: true };
    }

    if (segment.statusUpdatedAt) {
        return {
            start,
            end: Math.max(start, parseDate(segment.statusUpdatedAt).getTime()),
            known: true,
        };
    }

    return { start, end: start, known: false };
};

export const formatDuration = (ms: number): string => {
    const s = ms / 1000;
    if (s < 60) {
        return `${s.toFixed(1)}s`;
    }

    const pad = (n: number) => String(n).padStart(2, '0');
    const totalSeconds = Math.round(s);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (hours > 0) {
        return `${hours}h ${pad(minutes)}m`;
    }
    return `${minutes}m ${pad(seconds)}s`;
};
