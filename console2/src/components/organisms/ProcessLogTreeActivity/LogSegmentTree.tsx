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
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Icon, Menu } from 'semantic-ui-react';
import type { SemanticCOLORS, SemanticICONS } from 'semantic-ui-react';

import { SegmentStatus } from '../../../api/process/log';
import { isFinal } from '../../../api/process';
import type { ProcessStatus } from '../../../api/process';
import {
    categoryOf,
    describeNode,
    formatDuration,
    getAncestors,
    getSegmentTiming,
    isFailure,
    isGroup,
} from './segmentTree';
import type { SegmentNode, SegmentTree } from './segmentTree';

import SegmentedFilter from './SegmentedFilter';
import type { SegmentedOption } from './SegmentedFilter';

import './LogSegmentTree.css';

type StatusFilter = 'ALL' | 'FAILED' | 'WARNINGS' | 'RUNNING' | 'OK';

interface Props {
    tree: SegmentTree;
    processStatus?: ProcessStatus;
    selectedId?: number;
    onSelect: (segmentId: number) => void;
}

interface Row {
    node: SegmentNode;
    open: boolean;
    // shown only because one of the descendants matches the filter
    dimmed: boolean;
}

// filters and counters work with the real steps only, not with the groups
const matchesStatus = (n: SegmentNode, filter: StatusFilter) => {
    if (isGroup(n)) {
        return filter === 'ALL';
    }
    return filter === 'ALL' || categoryOf(n) === filter;
};

const LogSegmentTree = ({ tree, processStatus, selectedId, onSelect }: Props) => {
    // groups start collapsed (loops can have hundreds of iterations), the rest is expanded;
    // "toggled" keeps the nodes opened/closed by the user against that default
    const [toggled, setToggled] = useState<Set<number>>(new Set());
    const [query, setQuery] = useState<string>('');
    const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
    const [showTimeline, setShowTimeline] = useState<boolean>(true);
    const treeRef = useRef<HTMLDivElement>(null);

    const filterActive = statusFilter !== 'ALL' || query.length > 0;

    const isOpen = useCallback(
        (n: SegmentNode) => !isGroup(n) !== toggled.has(n.segment.id),
        [toggled]
    );

    const setOpen = useCallback((nodes: SegmentNode[], open: boolean) => {
        setToggled((prev) => {
            const next = new Set(prev);
            nodes.forEach((n) => {
                if (open === !isGroup(n)) {
                    next.delete(n.segment.id);
                } else {
                    next.add(n.segment.id);
                }
            });
            return next;
        });
    }, []);

    const rows = useMemo((): Row[] => {
        const q = query.toLowerCase();
        const selfMatch = (n: SegmentNode) =>
            matchesStatus(n, statusFilter) &&
            (!q ||
                n.segment.name.toLowerCase().includes(q) ||
                describeNode(n).name.toLowerCase().includes(q));

        // with an active filter keep the matching nodes and their ancestors
        const keep = new Map<SegmentNode, boolean>();
        const computeKeep = (n: SegmentNode): boolean => {
            const childKept = n.children.map(computeKeep).some(Boolean);
            const k = childKept || selfMatch(n);
            keep.set(n, k);
            return k;
        };
        tree.roots.forEach(computeKeep);

        const result: Row[] = [];
        const walk = (n: SegmentNode) => {
            if (filterActive && !keep.get(n)) {
                return;
            }

            const open = filterActive ? n.children.some((c) => keep.get(c)) : isOpen(n);

            result.push({ node: n, open, dimmed: filterActive && !selfMatch(n) });

            if (open) {
                n.children.forEach(walk);
            }
        };
        tree.roots.forEach(walk);

        return result;
    }, [tree, isOpen, query, statusFilter, filterActive]);

    const counts = useMemo(() => {
        const nodes = tree.ordered.filter((n) => !isGroup(n));
        return {
            ALL: nodes.length,
            FAILED: nodes.filter((n) => matchesStatus(n, 'FAILED')).length,
            WARNINGS: nodes.filter((n) => matchesStatus(n, 'WARNINGS')).length,
            RUNNING: nodes.filter((n) => matchesStatus(n, 'RUNNING')).length,
            OK: nodes.filter((n) => matchesStatus(n, 'OK')).length,
        };
    }, [tree]);

    // positions (in the tree order) of the segments with errors
    const errorPositions = useMemo(
        () =>
            tree.ordered
                .map((n, pos) => ({ n, pos }))
                .filter(
                    ({ n }) =>
                        !isGroup(n) &&
                        !n.recovered &&
                        (isFailure(n) || (n.segment.errors ?? 0) > 0)
                )
                .map(({ pos }) => pos),
        [tree]
    );

    const selectedPos =
        selectedId !== undefined ? tree.ordered.findIndex((n) => n.segment.id === selectedId) : -1;
    const selectedErrorIdx = errorPositions.indexOf(selectedPos);

    const timeRange = useMemo(() => {
        const now = Date.now();
        let start = Number.MAX_SAFE_INTEGER;
        let end = 0;
        tree.ordered.forEach((n) => {
            const t = getSegmentTiming(n.segment, processStatus, now);
            start = Math.min(start, t.start);
            if (t.known) {
                end = Math.max(end, t.end);
            }
        });
        return { start, end, now };
    }, [tree, processStatus]);

    // make sure the selected segment is visible: expand its ancestors and scroll to it
    useEffect(() => {
        if (selectedId === undefined) {
            return;
        }

        const node = tree.byId.get(selectedId);
        if (!node) {
            return;
        }

        const ancestors = getAncestors(node);
        if (ancestors.some((a) => !isOpen(a))) {
            setOpen(ancestors, true);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedId, tree]);

    // scroll to the selected row once per selection. The row can appear only after its ancestors
    // are expanded, but expanding/collapsing other nodes must not move the tree back to it
    const scrollPending = useRef<boolean>(false);
    useEffect(() => {
        scrollPending.current = true;
    }, [selectedId]);

    useEffect(() => {
        if (!scrollPending.current || selectedId === undefined || !treeRef.current) {
            return;
        }
        const el = treeRef.current.querySelector(`[data-segment-id="${selectedId}"]`);
        if (el) {
            el.scrollIntoView({ block: 'nearest' });
            scrollPending.current = false;
        }
    }, [selectedId, rows]);

    const toggle = useCallback(
        (segmentId: number, open?: boolean) => {
            const node = tree.byId.get(segmentId);
            if (node) {
                setOpen([node], open === undefined ? !isOpen(node) : open);
            }
        },
        [tree, isOpen, setOpen]
    );

    const expandAll = useCallback(() => {
        setOpen(
            tree.ordered.filter((n) => n.children.length > 0),
            true
        );
    }, [tree, setOpen]);

    const collapseAll = useCallback(() => {
        const selected = selectedId !== undefined ? tree.byId.get(selectedId) : undefined;
        const keepOpen = new Set(selected ? getAncestors(selected).map((a) => a.segment.id) : []);
        setOpen(
            tree.ordered.filter((n) => n.children.length > 0 && !keepOpen.has(n.segment.id)),
            false
        );
    }, [tree, selectedId, setOpen]);

    // jumps to the next/previous error relative to the selected step, wrapping around
    const gotoError = useCallback(
        (direction: 1 | -1) => {
            if (errorPositions.length === 0) {
                return;
            }
            const pos =
                direction > 0
                    ? errorPositions.find((p) => p > selectedPos) ?? errorPositions[0]
                    : [...errorPositions].reverse().find((p) => p < selectedPos) ??
                      errorPositions[errorPositions.length - 1];
            onSelect(tree.ordered[pos].segment.id);
        },
        [errorPositions, selectedPos, tree, onSelect]
    );

    const errorCount = errorPositions.length;
    const errorLabel =
        errorCount === 0
            ? 'No errors'
            : selectedErrorIdx >= 0
            ? `Error ${selectedErrorIdx + 1} of ${errorCount}`
            : `${errorCount} ${errorCount === 1 ? 'error' : 'errors'}`;

    const keyDownHandler = useCallback(
        (ev: React.KeyboardEvent<HTMLDivElement>) => {
            const idx = rows.findIndex((r) => r.node.segment.id === selectedId);
            const row = idx >= 0 ? rows[idx] : undefined;

            switch (ev.key) {
                case 'ArrowDown': {
                    const next = rows[Math.min(idx + 1, rows.length - 1)];
                    if (next) {
                        onSelect(next.node.segment.id);
                    }
                    break;
                }
                case 'ArrowUp': {
                    const prev = rows[Math.max(idx - 1, 0)];
                    if (prev) {
                        onSelect(prev.node.segment.id);
                    }
                    break;
                }
                case 'ArrowRight': {
                    if (!row || row.node.children.length === 0) {
                        return;
                    }
                    if (!row.open) {
                        toggle(row.node.segment.id, true);
                    } else if (rows[idx + 1]) {
                        onSelect(rows[idx + 1].node.segment.id);
                    }
                    break;
                }
                case 'ArrowLeft': {
                    if (!row) {
                        return;
                    }
                    if (row.open && row.node.children.length > 0) {
                        toggle(row.node.segment.id, false);
                    } else if (row.node.parent) {
                        onSelect(row.node.parent.segment.id);
                    }
                    break;
                }
                default:
                    return;
            }
            ev.preventDefault();
        },
        [rows, selectedId, onSelect, toggle]
    );

    const span = Math.max(timeRange.end - timeRange.start, 1);

    const statusOptions: SegmentedOption<StatusFilter>[] = [
        { value: 'ALL', label: 'All', count: counts.ALL },
        { value: 'FAILED', label: 'Failed', count: counts.FAILED },
        {
            value: 'WARNINGS',
            label: 'Warnings',
            count: counts.WARNINGS,
        },
        { value: 'OK', label: 'OK', count: counts.OK },
    ];
    if (counts.RUNNING > 0 || statusFilter === 'RUNNING') {
        statusOptions.splice(3, 0, {
            value: 'RUNNING',
            label: 'Running',
            count: counts.RUNNING,
        });
    }

    return (
        <div className={`LogSegmentTree${showTimeline ? '' : ' NoTimeline'}`}>
            <div className="TreeTools">
                <div className="ToolsRow">
                    <div className="Search">
                        <Icon name="search" />
                        <input
                            type="text"
                            placeholder="Filter steps..."
                            value={query}
                            onChange={(ev) => setQuery(ev.target.value)}
                        />
                    </div>
                    <div className="ToolGroup">
                        <button
                            type="button"
                            aria-label="Expand all"
                            data-tooltip="Expand all"
                            data-position="bottom center"
                            data-inverted=""
                            onClick={expandAll}
                        >
                            <Icon name="plus square outline" />
                        </button>
                        <button
                            type="button"
                            aria-label="Collapse all"
                            data-tooltip="Collapse all"
                            data-position="bottom center"
                            data-inverted=""
                            onClick={collapseAll}
                        >
                            <Icon name="minus square outline" />
                        </button>
                    </div>
                    <div className="ToolGroup">
                        <button
                            type="button"
                            aria-label={showTimeline ? 'Hide timeline' : 'Show timeline'}
                            data-tooltip={showTimeline ? 'Hide timeline' : 'Show timeline'}
                            data-position="bottom right"
                            data-inverted=""
                            className={showTimeline ? 'Active' : undefined}
                            aria-pressed={showTimeline}
                            onClick={() => setShowTimeline((prev) => !prev)}
                        >
                            <Icon name="align left" />
                        </button>
                    </div>
                </div>
                <div className="ToolsRow">
                    <div className="Chips">
                        <SegmentedFilter<StatusFilter>
                            value={statusFilter}
                            onChange={setStatusFilter}
                            options={statusOptions}
                        />
                    </div>
                    <Menu secondary={true} compact={true} size="mini" className="ErrorNav">
                        <Menu.Item
                            aria-label="Previous error"
                            data-tooltip="Previous error"
                            data-position="bottom center"
                            data-inverted=""
                            disabled={errorCount === 0}
                            onClick={() => gotoError(-1)}
                        >
                            <Icon name="chevron left" />
                        </Menu.Item>
                        <span className="ErrorLabel">{errorLabel}</span>
                        <Menu.Item
                            aria-label="Next error"
                            data-tooltip="Next error"
                            data-position="bottom right"
                            data-inverted=""
                            disabled={errorCount === 0}
                            onClick={() => gotoError(1)}
                        >
                            <Icon name="chevron right" />
                        </Menu.Item>
                    </Menu>
                </div>
            </div>

            <div className="TreeHeader">
                <span className="HeaderName">Step</span>
                <span className="HeaderDuration">Duration</span>
                <span className="HeaderTimeline">
                    <span>0s</span>
                    <span>{formatDuration(span)}</span>
                </span>
            </div>

            <div
                className="TreeRows"
                ref={treeRef}
                tabIndex={0}
                role="tree"
                onKeyDown={keyDownHandler}
            >
                {rows.length === 0 && <div className="Empty">No steps match the filter.</div>}

                {rows.map(({ node, open, dimmed }) => {
                    const s = node.segment;
                    const hasChildren = node.children.length > 0;
                    const timing = getSegmentTiming(s, processStatus, timeRange.now);
                    const { kind, name, note } = describeNode(node);

                    // collapsed nodes show counters of all their descendants
                    const warnings = hasChildren && !open ? node.totalWarnings : s.warnings ?? 0;
                    const errors = hasChildren && !open ? node.totalErrors : s.errors ?? 0;

                    return (
                        <div
                            key={s.id}
                            data-segment-id={s.id}
                            role="treeitem"
                            aria-selected={s.id === selectedId}
                            aria-expanded={hasChildren ? open : undefined}
                            className={`TreeRow${s.id === selectedId ? ' Selected' : ''}${
                                dimmed ? ' Dimmed' : ''
                            }${isGroup(node) ? ' Group' : ''}`}
                            onClick={() => onSelect(s.id)}
                            onDoubleClick={() => hasChildren && toggle(s.id)}
                        >
                            {Array.from({ length: node.depth }, (_, i) => (
                                <span key={i} className="Guide" />
                            ))}
                            <span
                                className="Caret"
                                onClick={(ev) => {
                                    if (hasChildren) {
                                        ev.stopPropagation();
                                        toggle(s.id);
                                    }
                                }}
                            >
                                {hasChildren && <Icon name={open ? 'caret down' : 'caret right'} />}
                            </span>
                            <SegmentStatusIcon
                                status={s.status}
                                processStatus={processStatus}
                                failedInside={!open && node.hasFailedDescendant}
                                retried={node.retried}
                                recovered={node.recovered}
                            />
                            {kind && <span className={`Kind Kind-${kind}`}>{kind}</span>}
                            <span className="Name" title={s.name}>
                                {name}
                            </span>
                            {note && (
                                <span className={`Note${node.retried ? ' Retried' : ''}`}>
                                    {note}
                                </span>
                            )}
                            {errors > 0 && (
                                <span className="Counter Errors" title="errors">
                                    {errors}
                                </span>
                            )}
                            {warnings > 0 && (
                                <span className="Counter Warnings" title="warnings">
                                    {warnings}
                                </span>
                            )}
                            <span className="Duration">
                                {timing.known && formatDuration(timing.end - timing.start)}
                            </span>
                            <span className="Timeline">
                                {timing.known && (
                                    <i
                                        className={`Bar Bar-${s.status ?? 'NONE'}`}
                                        style={{
                                            left: `${
                                                ((timing.start - timeRange.start) / span) * 100
                                            }%`,
                                            width: `${((timing.end - timing.start) / span) * 100}%`,
                                        }}
                                    />
                                )}
                            </span>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

interface SegmentStatusIconProps {
    status?: SegmentStatus;
    processStatus?: ProcessStatus;
    failedInside: boolean;
    retried?: boolean;
    recovered?: boolean;
}

export const SegmentStatusIcon = ({
    status,
    processStatus,
    failedInside,
    retried,
    recovered,
}: SegmentStatusIconProps) => {
    let color: SemanticCOLORS = 'grey';
    let icon: SemanticICONS = 'circle outline';
    let spinning = false;

    const recoveredFailure = recovered && status === SegmentStatus.FAILED;
    if (recoveredFailure || retried) {
        color = 'orange';
        icon = 'redo';
    } else if (status === SegmentStatus.RUNNING && isFinal(processStatus)) {
        color = 'yellow';
        icon = 'question circle';
    } else if (status === SegmentStatus.RUNNING) {
        color = 'teal';
        icon = 'spinner';
        spinning = true;
    } else if (status === SegmentStatus.SUSPENDED) {
        color = 'blue';
        icon = 'hourglass half';
    } else if (status === SegmentStatus.FAILED) {
        color = 'red';
        icon = 'times circle';
    } else if (failedInside) {
        color = 'red';
        icon = 'times circle outline';
    } else if (status === SegmentStatus.OK) {
        color = 'green';
        icon = 'check circle';
    }

    return (
        <Icon
            className="StatusIcon"
            loading={spinning}
            name={icon}
            color={color}
            title={recoveredFailure ? 'RECOVERED' : retried ? 'RETRIED' : status ?? ''}
        />
    );
};

export default LogSegmentTree;
