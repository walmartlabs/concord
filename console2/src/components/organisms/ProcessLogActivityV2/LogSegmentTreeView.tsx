/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Icon } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { LogSegmentTreeNode, SegmentStatus } from '../../../api/process/log';
import { ProcessStatus } from '../../../api/process';
import { LogProcessorOptions } from '../../../state/data/processes/logs/processors';
import LogSegmentActivity from './LogSegmentActivity';

import './styles.css';

interface FlatTreeNode {
    node: LogSegmentTreeNode;
    depth: number;
    hasChildren: boolean;
    collapsed: boolean;
}

const flattenTree = (
    nodes: LogSegmentTreeNode[],
    collapsedNodeIds: Set<number>,
    depth = 0
): FlatTreeNode[] => {
    return nodes.reduce<FlatTreeNode[]>((acc, node) => {
        const hasChildren = node.children.length > 0;
        const collapsed = hasChildren && collapsedNodeIds.has(node.id);

        acc.push({ node, depth, hasChildren, collapsed });
        if (hasChildren && !collapsed) {
            acc.push(...flattenTree(node.children, collapsedNodeIds, depth + 1));
        }
        return acc;
    }, []);
};

const getCollapsedStorageKey = (instanceId: ConcordId): string =>
    `processLogFlowCollapsed:${instanceId}`;

const collectNodeIdsWithChildren = (nodes: LogSegmentTreeNode[]): number[] => {
    return nodes.reduce<number[]>((acc, node) => {
        if (node.children.length > 0) {
            acc.push(node.id);
            acc.push(...collectNodeIdsWithChildren(node.children));
        }
        return acc;
    }, []);
};

const collectLeafNodeIds = (nodes: LogSegmentTreeNode[]): number[] => {
    return nodes.reduce<number[]>((acc, node) => {
        if (node.children.length === 0) {
            acc.push(node.id);
        } else {
            acc.push(...collectLeafNodeIds(node.children));
        }
        return acc;
    }, []);
};

interface FlowSummaryStats {
    failed: number;
    warnings: number;
    running: number;
}

const summarizeStats = (nodes: FlatTreeNode[]): FlowSummaryStats => {
    return nodes.reduce<FlowSummaryStats>(
        (acc, { node }) => {
            if (node.status === SegmentStatus.FAILED) {
                acc.failed += 1;
            }
            if ((node.warnings || 0) > 0) {
                acc.warnings += 1;
            }
            if (node.status === SegmentStatus.RUNNING) {
                acc.running += 1;
            }
            return acc;
        },
        { failed: 0, warnings: 0, running: 0 }
    );
};

interface LogSegmentTreeViewProps {
    segments: LogSegmentTreeNode[];
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    opts: LogProcessorOptions;
    forceRefresh: boolean;
    forceOpen: boolean;
}

const LogSegmentTreeView = ({
    segments,
    instanceId,
    processStatus,
    opts,
    forceRefresh,
    forceOpen,
}: LogSegmentTreeViewProps) => {
    const [collapsedNodeIds, setCollapsedNodeIds] = useState<Set<number>>(() => {
        try {
            const raw = localStorage.getItem(getCollapsedStorageKey(instanceId));
            if (!raw) {
                return new Set<number>();
            }
            const parsed = JSON.parse(raw) as number[];
            return new Set(parsed.filter((v) => typeof v === 'number'));
        } catch (_e) {
            return new Set<number>();
        }
    });
    const [expandedLogNodeIds, setExpandedLogNodeIds] = useState<Set<number>>(new Set());

    const toggleCollapsed = useCallback((nodeId: number) => {
        setCollapsedNodeIds((prev) => {
            const next = new Set(prev);
            if (next.has(nodeId)) {
                next.delete(nodeId);
            } else {
                next.add(nodeId);
            }
            return next;
        });
    }, []);

    const toggleStepLog = useCallback((nodeId: number) => {
        setExpandedLogNodeIds((prev) => {
            const next = new Set(prev);
            if (next.has(nodeId)) {
                next.delete(nodeId);
            } else {
                next.add(nodeId);
            }
            return next;
        });
    }, []);

    useEffect(() => {
        const key = getCollapsedStorageKey(instanceId);
        const value = JSON.stringify(Array.from(collapsedNodeIds));
        localStorage.setItem(key, value);
    }, [instanceId, collapsedNodeIds]);

    const flatNodes = useMemo(
        () => flattenTree(segments, collapsedNodeIds),
        [segments, collapsedNodeIds]
    );

    const collapsibleNodeIds = useMemo(() => collectNodeIdsWithChildren(segments), [segments]);
    const leafNodeIds = useMemo(() => collectLeafNodeIds(segments), [segments]);
    const totalNodes = useMemo(() => flattenTree(segments, new Set()).length, [segments]);
    const totalStats = useMemo(() => summarizeStats(flatNodes), [flatNodes]);
    const visibleNodes = flatNodes.length;
    const hiddenNodes = Math.max(totalNodes - visibleNodes, 0);
    const isFullyCollapsed =
        collapsibleNodeIds.length > 0 && collapsedNodeIds.size >= collapsibleNodeIds.length;

    useEffect(() => {
        if (forceOpen) {
            setExpandedLogNodeIds(new Set(leafNodeIds));
        } else {
            setExpandedLogNodeIds(new Set());
        }
    }, [forceOpen, leafNodeIds]);

    return (
        <div className="FlowTreeView">
            <div className="FlowTreeHeader">
                <div className="FlowTreeSummary">
                    {totalStats.failed > 0 ? (
                        <span className="FlowTreeSummaryPill FlowTreeSummaryPillError">
                            Failed {totalStats.failed}
                        </span>
                    ) : (
                        <span className="FlowTreeSummaryPill FlowTreeSummaryPillOk">
                            No failures
                        </span>
                    )}
                    <span className="FlowTreeSummaryPill FlowTreeSummaryPillWarn">
                        Warnings {totalStats.warnings}
                    </span>
                    <span className="FlowTreeSummaryPill FlowTreeSummaryPillRun">
                        Running {totalStats.running}
                    </span>
                    {hiddenNodes > 0 && (
                        <span className="FlowTreeSummaryItem">
                            Hidden {hiddenNodes} of {totalNodes}
                        </span>
                    )}
                </div>
                <Button.Group size="mini">
                    <Button
                        basic={true}
                        disabled={collapsedNodeIds.size === 0}
                        onClick={() => setCollapsedNodeIds(new Set())}>
                        Expand all
                    </Button>
                    <Button
                        basic={true}
                        disabled={isFullyCollapsed || collapsibleNodeIds.length === 0}
                        onClick={() => setCollapsedNodeIds(new Set(collapsibleNodeIds))}>
                        Collapse all
                    </Button>
                </Button.Group>
            </div>

            {flatNodes.map(({ node, depth, hasChildren, collapsed }) => {
                const showParallelMeta = node.isParallel === true;
                const showThreadMeta = showParallelMeta && node.threadId != null;
                const hasMeta = showParallelMeta || showThreadMeta;
                const indent = Math.min(depth, 10) * 18;

                return (
                    <div
                        key={node.id}
                        className={`FlowTreeNode${depth > 0 ? ' FlowTreeNodeNested' : ''}`}
                        style={{ marginLeft: indent }}
                    >
                        {hasMeta && (
                            <div className="FlowTreeNodeMeta">
                                {showThreadMeta && (
                                    <span className="FlowTreeBadge">thread {node.threadId}</span>
                                )}
                                {showParallelMeta && (
                                    <span className="FlowTreeBadge FlowTreeBadgeParallel">
                                        parallel
                                    </span>
                                )}
                            </div>
                        )}

                        <div className="FlowTreeNodeRow">
                            {hasChildren ? (
                                <button
                                    className="FlowTreeToggle"
                                    onClick={(e) => {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        toggleCollapsed(node.id);
                                    }}
                                    title={collapsed ? 'Expand children' : 'Collapse children'}
                                    type="button"
                                >
                                    <Icon
                                        name={
                                            collapsed ? 'caret right' : 'caret down'
                                        }
                                    />
                                </button>
                            ) : (
                                <button
                                    className="FlowTreeToggle FlowTreeToggleStep"
                                    onClick={(e) => {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        toggleStepLog(node.id);
                                    }}
                                    title={
                                        expandedLogNodeIds.has(node.id)
                                            ? 'Hide step log'
                                            : 'Show step log'
                                    }
                                    type="button"
                                >
                                    <Icon
                                        name={
                                            expandedLogNodeIds.has(node.id)
                                                ? 'caret down'
                                                : 'caret right'
                                        }
                                    />
                                </button>
                            )}

                            <div className="FlowTreeNodeSegment">
                                <LogSegmentActivity
                                    instanceId={instanceId}
                                    segmentId={node.id}
                                    correlationId={node.correlationId}
                                    name={node.name}
                                    createdAt={node.createdAt}
                                    status={node.status}
                                    statusUpdatedAt={node.statusUpdatedAt}
                                    warnings={node.warnings}
                                    errors={node.errors}
                                    processStatus={processStatus}
                                    opts={opts}
                                    forceRefresh={forceRefresh}
                                    forceOpen={forceOpen}
                                    hideCaret={true}
                                    disableHeaderToggle={true}
                                    controlledOpen={expandedLogNodeIds.has(node.id)}
                                />
                            </div>
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

export default LogSegmentTreeView;
