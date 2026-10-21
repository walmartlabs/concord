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

import SegmentName from './SegmentName';
import { TreeLoader } from './TreeLoader';
import * as React from 'react';
import { flushSync } from 'react-dom';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
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
    startsCollapsed,
} from './segmentTree';
import type { SegmentNode, SegmentTree } from './segmentTree';

import KindBadge from './KindBadge';
import SegmentedFilter from './SegmentedFilter';
import type { SegmentedOption } from './SegmentedFilter';

import './LogSegmentTree.css';

type StatusFilter = 'ALL' | 'FAILED' | 'WARNINGS' | 'RUNNING' | 'OK';

const ROW_HEIGHT = 28;
const OVERSCAN_ROWS = 5;

interface Props {
    remote?: TreeLoader;
    tree: SegmentTree;
    processStatus?: ProcessStatus;
    selectedId?: number;
    onSelect: (segmentId: number) => void;
}

interface Row {
    pageKey?: string;
    node: SegmentNode;
    open: boolean;
    // shown only because one of the descendants matches the filter
    dimmed: boolean;
}

interface TreeTimeRange {
    start: number;
    end: number;
    now: number;
}

interface SegmentTreeRowProps {
    loadingChildren?: boolean;
    row: Row;
    selected: boolean;
    processStatus?: ProcessStatus;
    timeRange: TreeTimeRange;
    onSelect: (segmentId: number) => void;
    onToggle: (segmentId: number, open?: boolean) => void;
}

const SegmentTreeRow = React.memo(
    ({
        row: { node, open, dimmed },
        loadingChildren,
        selected,
        processStatus,
        timeRange,
        onSelect,
        onToggle,
    }: SegmentTreeRowProps) => {
        const s = node.segment;
        const hasChildren = node.remote?.hasChildren ?? node.children.length > 0;
        const timing = getSegmentTiming(s, processStatus, timeRange.now);
        const { kind, name, note, badgeOnly, loop } = describeNode(node);
        const span = Math.max(timeRange.end - timeRange.start, 1);

        // collapsed nodes show counters of all their descendants
        const warnings =
            hasChildren && !open && !node.remote ? node.totalWarnings : s.warnings ?? 0;
        const errors = hasChildren && !open && !node.remote ? node.totalErrors : s.errors ?? 0;

        return (
            <div
                data-segment-id={s.id}
                role="treeitem"
                aria-selected={selected}
                aria-expanded={hasChildren ? open : undefined}
                className={`TreeRow${selected ? ' Selected' : ''}${dimmed ? ' Dimmed' : ''}`}
                onClick={() => onSelect(s.id)}
                onDoubleClick={() => hasChildren && onToggle(s.id)}
            >
                {Array.from({ length: node.depth }, (_, i) => (
                    <span key={i} className="Guide" />
                ))}
                <span
                    className="Caret"
                    onClick={(ev) => {
                        if (hasChildren) {
                            ev.stopPropagation();
                            onToggle(s.id);
                        }
                    }}
                >
                    {hasChildren &&
                        (loadingChildren ? (
                            <Icon name="spinner" loading title="Loading child steps" />
                        ) : (
                            <Icon name={open ? 'caret down' : 'caret right'} />
                        ))}
                </span>
                <SegmentStatusIcon
                    status={s.status}
                    processStatus={processStatus}
                    failedInside={!open && node.hasFailedDescendant}
                    retried={node.retried}
                />
                {loop && <KindBadge kind="loop" />}
                {kind && <KindBadge kind={kind} title={badgeOnly ? name : undefined} />}
                <span className="Name" title={badgeOnly ? undefined : s.name}>
                    {badgeOnly ? null : <SegmentName name={name} />}
                </span>
                {note && <span className={`Note${node.retried ? ' Retried' : ''}`}>{note}</span>}
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
                                left: `${((timing.start - timeRange.start) / span) * 100}%`,
                                width: `${((timing.end - timing.start) / span) * 100}%`,
                            }}
                        />
                    )}
                </span>
            </div>
        );
    }
);

const matchesStatus = (n: SegmentNode, filter: StatusFilter) =>
    filter === 'ALL' || categoryOf(n) === filter;

const LogSegmentTree = ({ tree, processStatus, selectedId, onSelect, remote }: Props) => {
    // loops start collapsed (they can have hundreds of iterations), the rest is expanded;
    // "toggled" keeps the nodes opened/closed by the user against that default
    const [toggled, setToggled] = useState<Set<number>>(new Set());
    const [query, setQuery] = useState<string>('');
    const [filteredCollapse, setFilteredCollapse] = useState<{ key: string; ids: Set<number> }>({
        key: '',
        ids: new Set(),
    });
    const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
    const [showTimeline, setShowTimeline] = useState<boolean>(true);
    const treeRef = useRef<HTMLDivElement>(null);
    const [viewport, setViewport] = useState({ height: 0, scrollTop: 0 });
    const updateViewport = useCallback(() => {
        const el = treeRef.current;
        if (!el) {
            return;
        }
        const height = el.clientHeight;
        const scrollTop = el.scrollTop;
        setViewport((prev) =>
            prev.height === height &&
            Math.floor(prev.scrollTop / ROW_HEIGHT) === Math.floor(scrollTop / ROW_HEIGHT) &&
            Math.ceil((prev.scrollTop + height) / ROW_HEIGHT) ===
                Math.ceil((scrollTop + height) / ROW_HEIGHT)
                ? prev
                : { height, scrollTop }
        );
    }, []);

    useLayoutEffect(() => {
        const el = treeRef.current;
        if (!el) {
            return;
        }
        updateViewport();
        const observer = new ResizeObserver(updateViewport);
        observer.observe(el);
        return () => observer.disconnect();
    }, [updateViewport]);

    const previousStatusFilter = useRef(statusFilter);
    useEffect(() => {
        const statusChanged = previousStatusFilter.current !== statusFilter;
        previousStatusFilter.current = statusFilter;
        if (!remote) return;
        if (statusChanged) {
            remote.search(query.trim(), statusFilter === 'ALL' ? undefined : statusFilter);
            return;
        }
        const timer = window.setTimeout(
            () => remote.search(query.trim(), statusFilter === 'ALL' ? undefined : statusFilter),
            250
        );
        return () => window.clearTimeout(timer);
    }, [remote, query, statusFilter]);

    const filterActive = remote ? remote.searching : statusFilter !== 'ALL' || query.length > 0;

    const filterKey = JSON.stringify(
        remote ? [remote.query, remote.category] : [query, statusFilter]
    );
    const collapsedInFilter = filteredCollapse.key === filterKey ? filteredCollapse.ids : undefined;

    useEffect(() => {
        setFilteredCollapse({ key: filterKey, ids: new Set() });
    }, [filterKey]);

    const isOpen = useCallback(
        (n: SegmentNode) =>
            filterActive
                ? !collapsedInFilter?.has(n.segment.id)
                : remote
                ? remote.expanded.has(n.segment.id)
                : !startsCollapsed(n) !== toggled.has(n.segment.id),
        [toggled, remote, tree, filterActive, collapsedInFilter]
    );

    const setOpen = useCallback((nodes: SegmentNode[], open: boolean) => {
        setToggled((prev) => {
            const next = new Set(prev);
            nodes.forEach((n) => {
                if (open === !startsCollapsed(n)) {
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
            remote
                ? remote.matches.has(n.segment.id)
                : matchesStatus(n, statusFilter) &&
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
        const more = (node: SegmentNode, parentId?: number) => {
            if (!remote) return;
            const branch = remote.branch(parentId);
            // Refreshing an existing page must not insert a row and shift the viewport.
            const loadingNewPage = branch?.loading && branch.loadingIndex === branch.chunks.length;
            if (
                (loadingNewPage && (parentId === undefined || branch.chunks.length > 0)) ||
                branch?.error ||
                remote.hasMore(parentId)
            ) {
                result.push({
                    node,
                    open: false,
                    dimmed: false,
                    pageKey: parentId === undefined ? 'root' : String(parentId),
                });
            }
        };
        const walk = (n: SegmentNode) => {
            if (filterActive && !keep.get(n)) {
                return;
            }

            const open = isOpen(n) && (!filterActive || n.children.some((c) => keep.get(c)));

            result.push({ node: n, open, dimmed: filterActive && !selfMatch(n) });

            if (open) {
                n.children.forEach(walk);
                if (!filterActive && n.remote?.hasChildren) more(n, n.segment.id);
            }
        };
        tree.roots.forEach(walk);
        if (tree.roots.length) more(tree.roots[0]);

        return result;
    }, [tree, isOpen, query, statusFilter, filterActive, remote]);

    const counts = useMemo(() => {
        const nodes = tree.ordered;
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
                .filter(({ n }) => isFailure(n) || (n.segment.errors ?? 0) > 0)
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
        if (remote || selectedId === undefined) {
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
    useLayoutEffect(() => {
        scrollPending.current = true;
    }, [selectedId]);

    useLayoutEffect(() => {
        const el = treeRef.current;
        if (!el) {
            return;
        }
        const height = el.clientHeight;
        const maxTop = Math.max(0, rows.length * ROW_HEIGHT + 12 - height);
        const clamp = (offset: number) => Math.max(0, Math.min(offset, maxTop));
        const clampedTop = clamp(el.scrollTop);
        if (el.scrollTop !== clampedTop) {
            el.scrollTop = clampedTop;
        }
        updateViewport();
        if (selectedId === undefined) {
            scrollPending.current = false;
            return;
        }
        if (!scrollPending.current || height <= 0) {
            return;
        }
        const index = rows.findIndex((row) => row.node.segment.id === selectedId);
        if (index < 0) {
            return;
        }
        const top = index * ROW_HEIGHT;
        const bottom = top + ROW_HEIGHT;
        if (top < el.scrollTop) {
            el.scrollTop = clamp(top);
        } else if (bottom > el.scrollTop + height) {
            el.scrollTop = clamp(bottom - height);
        }
        updateViewport();
        scrollPending.current = false;
    }, [selectedId, rows, viewport.height, updateViewport]);

    const handleScroll = useCallback(() => {
        // Commit the new window during the scroll event, before the next paint.
        flushSync(updateViewport);
    }, [updateViewport]);

    const maxTop = Math.max(0, rows.length * ROW_HEIGHT + 12 - viewport.height);
    const overscan = Math.max(OVERSCAN_ROWS, Math.ceil(viewport.height / ROW_HEIGHT));
    const sampledTop = Math.min(viewport.scrollTop, maxTop);
    const start =
        rows.length === 0 ? 0 : Math.max(0, Math.floor(sampledTop / ROW_HEIGHT) - overscan);
    const end =
        rows.length === 0
            ? 0
            : Math.min(
                  rows.length,
                  Math.ceil((sampledTop + viewport.height) / ROW_HEIGHT) + overscan
              );
    const visibleRows = useMemo(() => rows.slice(start, end), [rows, start, end]);
    // A larger render buffer must not expand the server refresh window.
    const refreshStart = Math.max(0, Math.floor(sampledTop / ROW_HEIGHT) - OVERSCAN_ROWS);
    const refreshEnd = Math.min(
        rows.length,
        Math.ceil((sampledTop + viewport.height) / ROW_HEIGHT) + OVERSCAN_ROWS
    );
    const refreshRows = useMemo(
        () => rows.slice(refreshStart, refreshEnd),
        [rows, refreshStart, refreshEnd]
    );
    const visibleIdsKey = refreshRows
        .filter((r) => r.pageKey === undefined)
        .map((r) => r.node.segment.id)
        .join(',');

    useEffect(() => {
        const ids = refreshRows
            .filter((r) => r.pageKey === undefined)
            .map((r) => r.node.segment.id);
        if (!remote?.setVisible(ids)) return;
        // Update newly visible rows even after polling stops for a completed process.
        const timer = window.setTimeout(remote.refreshViewport, 150);
        return () => window.clearTimeout(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [remote, visibleIdsKey]);

    const toggle = useCallback(
        (segmentId: number, open?: boolean) => {
            const node = tree.byId.get(segmentId);
            if (node) {
                if (filterActive) {
                    const shouldOpen = open === undefined ? !isOpen(node) : open;
                    setFilteredCollapse((previous) => {
                        const ids = new Set(previous.key === filterKey ? previous.ids : []);
                        if (shouldOpen) ids.delete(segmentId);
                        else ids.add(segmentId);
                        return { key: filterKey, ids };
                    });
                    return;
                }
                if (remote) remote.toggle(segmentId, open === undefined ? !isOpen(node) : open);
                else setOpen([node], open === undefined ? !isOpen(node) : open);
            }
        },
        [tree, isOpen, setOpen, remote, filterActive, filterKey]
    );

    const expandAll = useCallback(() => {
        if (filterActive) {
            setFilteredCollapse({ key: filterKey, ids: new Set() });
            return;
        }
        setOpen(
            tree.ordered.filter((n) => n.children.length > 0),
            true
        );
    }, [tree, setOpen, filterActive, filterKey]);

    const collapseAll = useCallback(() => {
        if (filterActive) {
            setFilteredCollapse({
                key: filterKey,
                ids: new Set(
                    tree.ordered.filter((n) => n.children.length > 0).map((n) => n.segment.id)
                ),
            });
            return;
        }
        if (remote) {
            remote.collapseAll();
            return;
        }
        const selected = selectedId !== undefined ? tree.byId.get(selectedId) : undefined;
        const keepOpen = new Set(selected ? getAncestors(selected).map((a) => a.segment.id) : []);
        setOpen(
            tree.ordered.filter((n) => n.children.length > 0 && !keepOpen.has(n.segment.id)),
            false
        );
    }, [tree, selectedId, setOpen, remote, filterActive, filterKey]);

    // jumps to the next/previous error relative to the selected step, wrapping around
    const gotoError = useCallback(
        (direction: 1 | -1) => {
            if (remote) {
                setQuery('');
                setStatusFilter('ALL');
                remote.search('');
                remote.navigateError(direction);
                return;
            }
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
        [errorPositions, selectedPos, tree, onSelect, remote]
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
            const navigationRows = rows.filter((r) => r.pageKey === undefined);
            const idx = navigationRows.findIndex((r) => r.node.segment.id === selectedId);
            const row = idx >= 0 ? navigationRows[idx] : undefined;

            switch (ev.key) {
                case 'ArrowDown': {
                    const next = navigationRows[Math.min(idx + 1, navigationRows.length - 1)];
                    if (next) {
                        onSelect(next.node.segment.id);
                    }
                    break;
                }
                case 'ArrowUp': {
                    const prev = navigationRows[Math.max(idx - 1, 0)];
                    if (prev) {
                        onSelect(prev.node.segment.id);
                    }
                    break;
                }
                case 'ArrowRight': {
                    if (!row || !(row.node.remote?.hasChildren ?? row.node.children.length > 0)) {
                        return;
                    }
                    if (!row.open) {
                        toggle(row.node.segment.id, true);
                    } else if (navigationRows[idx + 1]) {
                        onSelect(navigationRows[idx + 1].node.segment.id);
                    }
                    break;
                }
                case 'ArrowLeft': {
                    if (!row) {
                        return;
                    }
                    if (
                        row.open &&
                        (row.node.remote?.hasChildren ?? row.node.children.length > 0)
                    ) {
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
        { value: 'ALL', label: 'All', count: remote ? undefined : counts.ALL },
        { value: 'FAILED', label: 'Failed', count: remote ? undefined : counts.FAILED },
        {
            value: 'WARNINGS',
            label: 'Warnings',
            count: remote ? undefined : counts.WARNINGS,
        },
        { value: 'OK', label: 'OK', count: remote ? undefined : counts.OK },
    ];
    if (remote || counts.RUNNING > 0 || statusFilter === 'RUNNING') {
        statusOptions.splice(3, 0, {
            value: 'RUNNING',
            label: 'Running',
            count: remote ? undefined : counts.RUNNING,
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
                        {!remote && (
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
                        )}
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
                            disabled={!remote && errorCount === 0}
                            onClick={() => gotoError(-1)}
                        >
                            <Icon name="chevron left" />
                        </Menu.Item>
                        <span className="ErrorLabel">{remote ? 'Errors' : errorLabel}</span>
                        <Menu.Item
                            aria-label="Next error"
                            data-tooltip="Next error"
                            data-position="bottom right"
                            data-inverted=""
                            disabled={!remote && errorCount === 0}
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
                onScroll={handleScroll}
            >
                {rows.length === 0 && (
                    <div className="Empty">
                        {remote?.branch()?.loading ? (
                            'Loading steps...'
                        ) : remote?.branch()?.error ? (
                            <button type="button" onClick={() => remote.retry()}>
                                Retry loading steps
                            </button>
                        ) : (
                            'No steps match the filter.'
                        )}
                    </div>
                )}

                <div
                    aria-hidden="true"
                    role="presentation"
                    style={{ height: start * ROW_HEIGHT }}
                />
                {visibleRows.map((row) =>
                    row.pageKey !== undefined && remote ? (
                        <div
                            key={`page:${row.pageKey}`}
                            className="TreePage"
                            style={{
                                height: ROW_HEIGHT,
                                paddingLeft:
                                    row.pageKey === 'root' ? 12 : (row.node.depth + 1) * 16 + 12,
                            }}
                        >
                            <button
                                type="button"
                                disabled={
                                    remote.branch(
                                        row.pageKey === 'root' ? undefined : Number(row.pageKey)
                                    )?.loading
                                }
                                onClick={() =>
                                    remote.retry(
                                        row.pageKey === 'root' ? undefined : Number(row.pageKey)
                                    )
                                }
                            >
                                {remote.branch(
                                    row.pageKey === 'root' ? undefined : Number(row.pageKey)
                                )?.loading
                                    ? 'Loading...'
                                    : remote.branch(
                                          row.pageKey === 'root' ? undefined : Number(row.pageKey)
                                      )?.error
                                    ? 'Retry loading steps'
                                    : 'Load more steps'}
                            </button>
                        </div>
                    ) : (
                        <SegmentTreeRow
                            key={row.node.segment.id}
                            row={row}
                            loadingChildren={
                                !filterActive &&
                                row.open &&
                                !!remote?.branch(row.node.segment.id)?.loading &&
                                remote.branch(row.node.segment.id)?.chunks.length === 0
                            }
                            selected={row.node.segment.id === selectedId}
                            processStatus={processStatus}
                            timeRange={timeRange}
                            onSelect={onSelect}
                            onToggle={toggle}
                        />
                    )
                )}
                <div
                    aria-hidden="true"
                    role="presentation"
                    style={{ height: (rows.length - end) * ROW_HEIGHT }}
                />
            </div>
        </div>
    );
};

interface SegmentStatusIconProps {
    status?: SegmentStatus;
    processStatus?: ProcessStatus;
    failedInside: boolean;
    retried?: boolean;
}

export const SegmentStatusIcon = ({
    status,
    processStatus,
    failedInside,
}: SegmentStatusIconProps) => {
    let color: SemanticCOLORS = 'grey';
    let icon: SemanticICONS = 'circle outline';
    let spinning = false;

    if (status === SegmentStatus.FAILED) {
        color = 'red';
        icon = 'times circle';

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
    } else if (failedInside && status !== SegmentStatus.OK) {
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
            title={status ?? ''}
        />
    );
};

export default React.memo(LogSegmentTree);
