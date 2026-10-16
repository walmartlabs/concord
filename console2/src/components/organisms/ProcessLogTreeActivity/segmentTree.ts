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

import { LogSegmentEntry, SegmentKind, SegmentStatus } from '../../../api/process/log';
import { isFinal, ProcessStatus } from '../../../api/process';

const SYSTEM_SEGMENT_ID = 0;

/**
 * What the segment represents, recorded by the runner:
 * - step: the whole execution of a step;
 * - iteration: one iteration of a loop;
 * - attempt: one attempt of a step with "retry";
 * - error: the "error" block of a step;
 * - parallel: the whole execution of a "parallel" block.
 * The tree is rendered as recorded, the client doesn't group the segments.
 */
export type NodeKind = 'step' | 'iteration' | 'attempt' | 'error' | 'parallel';

export interface SegmentNode {
    kind: NodeKind;
    segment: LogSegmentEntry;
    // a failed attempt followed by another one
    retried?: boolean;
    // a failure handled by an ancestor (a retry or an "error" block): not a failure of the process
    recovered?: boolean;
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

const KINDS: Record<SegmentKind, NodeKind> = {
    [SegmentKind.STEP]: 'step',
    [SegmentKind.ITERATION]: 'iteration',
    [SegmentKind.ATTEMPT]: 'attempt',
    [SegmentKind.ERROR]: 'error',
    [SegmentKind.PARALLEL]: 'parallel',
};

// segments of older runners have no kind
const kindOf = (s: LogSegmentEntry): NodeKind => (s.kind ? KINDS[s.kind] : undefined) ?? 'step';

// completed, possibly after a handled failure
const isCompleted = (status?: SegmentStatus) =>
    status === SegmentStatus.OK || status === SegmentStatus.RECOVERED;

// a real failure: failed, not retried and not handled by an ancestor
export const isFailure = (node: SegmentNode) =>
    node.segment.status === SegmentStatus.FAILED && !node.recovered && !node.retried;

// a loop: can have hundreds of iterations
export const startsCollapsed = (node: SegmentNode) =>
    node.children.some((c) => c.kind === 'iteration');

const newNode = (segment: LogSegmentEntry): SegmentNode => ({
    kind: kindOf(segment),
    segment,
    children: [],
    depth: 0,
    totalWarnings: 0,
    totalErrors: 0,
    hasFailedDescendant: false,
});

export const buildSegmentTree = (segments: LogSegmentEntry[]): SegmentTree => {
    const nodes = new Map<number, SegmentNode>();
    segments.forEach((s) => nodes.set(s.id, newNode(s)));

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

    const byId = new Map<number, SegmentNode>();
    const ordered: SegmentNode[] = [];
    const walk = (node: SegmentNode, parent: SegmentNode | undefined, depth: number) => {
        node.parent = parent;
        node.depth = depth;
        node.retried =
            node.kind === 'attempt' &&
            node.segment.status === SegmentStatus.FAILED &&
            (parent?.children ?? []).some(
                (c) => c.kind === 'attempt' && (c.segment.index ?? 0) > (node.segment.index ?? 0)
            );
        // the failure stopped at an ancestor that completed (a retry or an "error" block)
        // or inside an attempt that was retried
        node.recovered =
            node.segment.status === SegmentStatus.FAILED &&
            parent !== undefined &&
            (isCompleted(parent.segment.status) ||
                parent.recovered === true ||
                parent.retried === true);
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
    roots.forEach((r) => walk(r, undefined, 0));

    return { roots, byId, ordered };
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
    const steps = tree.ordered.filter((n) => n.segment.id !== SYSTEM_SEGMENT_ID);

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
        ((n.recovered || n.retried) && status === SegmentStatus.FAILED) ||
        status === SegmentStatus.RECOVERED ||
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
    // the tree shows the badge only, e.g. error handlers and parallel blocks
    badgeOnly?: boolean;
    // a step with iterations, shown with the "loop" badge before the kind
    loop?: boolean;
}

export const describeNode = (n: SegmentNode): NodeDescription => {
    const s = n.segment;
    if (s.id === SYSTEM_SEGMENT_ID) {
        return { name: 'System', kind: 'system' };
    }

    switch (n.kind) {
        case 'error':
            return { name: 'Error handler', kind: 'error handler', badgeOnly: true };
        case 'parallel':
            return { name: 'Parallel', kind: 'parallel', badgeOnly: true };
        case 'attempt':
            // structural names already have the number, e.g. "attempt #2"
            return {
                name: parseSegmentName(s.name).name,
                note: s.name.startsWith('attempt #')
                    ? n.retried
                        ? 'retried'
                        : undefined
                    : `attempt ${(s.index ?? 0) + 1}${n.retried ? ' · retried' : ''}`,
            };
        case 'iteration': {
            const item = `item #${(s.index ?? 0) + 1}`;
            // structural names, e.g. "iteration #2", are shown as the item's number
            return s.name.startsWith('iteration #') ? { name: item } : { name: s.name, note: item };
        }
    }

    const { kind, name } = parseSegmentName(s.name);
    const iterations = n.children.filter((c) => c.kind === 'iteration').length;
    const note =
        iterations > 0 ? `${iterations} ${iterations === 1 ? 'item' : 'items'}` : undefined;
    return iterations > 0 ? { name, kind, note, loop: true } : { name, kind, note };
};

const KIND_RE = /^(task|script|call): (.+)$/;

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
