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
import { Icon, SemanticICONS } from 'semantic-ui-react';
import { format as formatDate, parseISO as parseDate } from 'date-fns';

import type { ConcordId } from '../../../api/common';
import { isFinal } from '../../../api/process';
import type { ProcessStatus } from '../../../api/process';
import { getFullSegmentLog, LogTooLargeError, SegmentStatus } from '../../../api/process/log';
import RequestErrorMessage from '../../molecules/RequestErrorMessage';
import { SegmentStatusIcon } from './LogSegmentTree';
import { buildLogFlow, parseLog, splitLinks } from './logParser';
import type { FlowItem, FlowSegment, LogLevel, LogLine } from './logParser';
import { describeNode, isGroup } from './segmentTree';
import type { SegmentNode } from './segmentTree';

import { useSegmentLogs } from './useSegmentLogs';
import type { SegmentLog, SegmentLogRequest, SegmentLogs } from './useSegmentLogs';

import SegmentedFilter from './SegmentedFilter';

import './LogView.css';

const ROOT_TAIL_BYTES = 256 * 1024;
const NESTED_TAIL_BYTES = 64 * 1024;
const INITIAL_REQUEST_BUDGET_BYTES = 2 * 1024 * 1024;
const REQUEST_BUDGET_PAGE_BYTES = 2 * 1024 * 1024;
const INITIAL_RENDER_ITEMS = 2_000;
const RENDER_ITEM_PAGE = 2_000;
const MAX_COPY_SIZE_BYTES = 5 * 1024 * 1024;
const OPTS_KEY = 'logTree.logOpts';

type LevelFilter = 'all' | 'warn' | 'error';

interface LogOpts {
    level: LevelFilter;
    wrap: boolean;
    follow: boolean;
}

const DEFAULT_OPTS: LogOpts = { level: 'all', wrap: true, follow: true };

const loadOpts = (): LogOpts => {
    try {
        return { ...DEFAULT_OPTS, ...JSON.parse(localStorage.getItem(OPTS_KEY) ?? '{}') };
    } catch {
        return DEFAULT_OPTS;
    }
};

const LEVEL_RANK: Record<LogLevel, number> = { TRACE: 0, DEBUG: 1, INFO: 2, WARN: 3, ERROR: 4 };

const levelOk = (line: LogLine, filter: LevelFilter) => {
    if (filter === 'all') {
        return true;
    }
    const rank = line.level ? LEVEL_RANK[line.level] : LEVEL_RANK.INFO;
    return rank >= (filter === 'warn' ? LEVEL_RANK.WARN : LEVEL_RANK.ERROR);
};

// the real segments directly under the node, groups are unwrapped
const nestedSegments = (node: SegmentNode): SegmentNode[] =>
    node.children.flatMap((child) => (isGroup(child) ? nestedSegments(child) : [child]));

interface Props {
    instanceId: ConcordId;
    node?: SegmentNode;
    processStatus?: ProcessStatus;
    dataFetchInterval: number;
    forceRefresh: unknown;
    pollingEnabled: boolean;
    onSelect: (segmentId: number) => void;
}

type CopyState = 'idle' | 'copying' | 'copied' | 'failed' | 'tooLarge';

const COPY_LABELS: Record<CopyState, string> = {
    idle: 'Copy',
    copying: 'Copying...',
    copied: 'Copied',
    failed: 'Copy failed',
    tooLarge: 'Too large to copy',
};

const COPY_ICONS: Record<CopyState, SemanticICONS> = {
    idle: 'copy outline',
    copying: 'spinner',
    copied: 'check',
    failed: 'exclamation circle',
    tooLarge: 'exclamation triangle',
};

const isLive = (node: SegmentNode, processStatus?: ProcessStatus) =>
    node.segment.status === SegmentStatus.RUNNING && !isFinal(processStatus);

const LogView = ({
    instanceId,
    node,
    processStatus,
    dataFetchInterval,
    forceRefresh,
    pollingEnabled,
    onSelect,
}: Props) => {
    const [opts, setOpts] = useState<LogOpts>(loadOpts);
    const logs = useSegmentLogs(instanceId, {
        pollInterval: dataFetchInterval,
        pollingEnabled,
        refreshToken: forceRefresh,
    });

    useEffect(() => {
        localStorage.setItem(OPTS_KEY, JSON.stringify(opts));
    }, [opts]);

    useEffect(() => {
        if (!node) {
            logs.reconcile([]);
        }
    }, [logs, node]);

    return node ? (
        <SelectedLogView
            key={`${instanceId}:${node.segment.id}`}
            instanceId={instanceId}
            node={node}
            processStatus={processStatus}
            logs={logs}
            opts={opts}
            setOpts={setOpts}
            onSelect={onSelect}
        />
    ) : null;
};

interface SelectedLogViewProps {
    instanceId: ConcordId;
    node: SegmentNode;
    processStatus?: ProcessStatus;
    logs: SegmentLogs;
    opts: LogOpts;
    setOpts: React.Dispatch<React.SetStateAction<LogOpts>>;
    onSelect: (segmentId: number) => void;
}

const SelectedLogView = ({
    instanceId,
    node,
    processStatus,
    logs,
    opts,
    setOpts,
    onSelect,
}: SelectedLogViewProps) => {
    const segment = node.segment;
    const [fullIds, setFullIds] = useState<ReadonlySet<number>>(new Set());
    const [requestBudget, setRequestBudget] = useState(INITIAL_REQUEST_BUDGET_BYTES);
    const [renderItemLimit, setRenderItemLimit] = useState(INITIAL_RENDER_ITEMS);
    const [copyState, setCopyState] = useState<CopyState>('idle');
    const bottomRef = useRef<HTMLDivElement>(null);
    const parsed = useRef(new Map<number, { text: string; lines: LogLine[] }>());
    const copyGeneration = useRef(0);
    const copyTimer = useRef<number>();

    const ownLog = !isGroup(node);
    const { tree, included, hiddenCount } = useMemo(() => {
        let remaining = requestBudget - (ownLog ? ROOT_TAIL_BYTES : 0);
        const included: SegmentNode[] = [node];
        let hiddenCount = 0;

        const countNested = (current: SegmentNode): number =>
            nestedSegments(current).reduce((count, child) => count + 1 + countNested(child), 0);

        const build = (current: SegmentNode): FlowTreeNode => {
            const children: FlowTreeNode[] = [];
            nestedSegments(current).forEach((child) => {
                if (remaining >= NESTED_TAIL_BYTES) {
                    remaining -= NESTED_TAIL_BYTES;
                    included.push(child);
                    children.push(build(child));
                } else {
                    hiddenCount += 1 + countNested(child);
                }
            });
            return { node: current, children };
        };

        return { tree: build(node), included, hiddenCount };
    }, [node, ownLog, requestBudget]);

    const requests = useMemo(
        (): SegmentLogRequest[] =>
            included
                .filter((current) => !isGroup(current))
                .map((current) => ({
                    id: current.segment.id,
                    live: isLive(current, processStatus),
                    tailBytes: current === node ? ROOT_TAIL_BYTES : NESTED_TAIL_BYTES,
                    full: fullIds.has(current.segment.id),
                })),
        [included, node, processStatus, fullIds]
    );

    useEffect(() => {
        logs.reconcile(requests);
    }, [logs, requests]);

    useEffect(() => {
        const includedIds = new Set(included.map((current) => current.segment.id));
        parsed.current.forEach((_value, id) => {
            if (!includedIds.has(id)) {
                parsed.current.delete(id);
            }
        });
    }, [included]);

    useEffect(
        () => () => {
            copyGeneration.current++;
            if (copyTimer.current !== undefined) {
                window.clearTimeout(copyTimer.current);
            }
        },
        []
    );

    const linesOf = (id: number): LogLine[] => {
        const text = logs.get(id).text;
        const cached = parsed.current.get(id);
        if (cached && cached.text === text) {
            return cached.lines;
        }
        const lines = parseLog(text);
        parsed.current.set(id, { text, lines });
        return lines;
    };

    const toFlow = (current: FlowTreeNode): FlowSegment<SegmentNode> => ({
        segment: current.node,
        start: parseDate(current.node.segment.createdAt).getTime(),
        lines: linesOf(current.node.segment.id).filter((line) => levelOk(line, opts.level)),
        children: current.children.map(toFlow),
    });
    const allItems = buildLogFlow(toFlow(tree));
    let items: FlowItem<SegmentNode>[] = allItems.slice(-renderItemLimit);
    if (
        allItems.length > renderItemLimit &&
        items[0]?.kind === 'line' &&
        items[0].segment.segment.id !== segment.id
    ) {
        items = [
            { kind: 'header', segment: items[0].segment, continued: true },
            ...items.slice(1),
        ];
    }

    const rootLog: SegmentLog = ownLog
        ? logs.get(segment.id)
        : { text: '', truncated: false, loaded: true };
    const live = isLive(node, processStatus);
    const loadedBytes = included.reduce(
        (total, current) => total + logs.get(current.segment.id).text.length,
        0
    );

    useEffect(() => {
        if (live && opts.follow && bottomRef.current) {
            bottomRef.current.scrollIntoView({ block: 'end' });
        }
    }, [live, opts.follow, loadedBytes]);

    const loadFull = useCallback((id: number) => {
        setFullIds((previous) => new Set([...previous, id]));
    }, []);

    const copyHandler = useCallback(async () => {
        const generation = ++copyGeneration.current;
        setCopyState('copying');
        let result: CopyState;
        try {
            const text = await getFullSegmentLog(instanceId, segment.id, MAX_COPY_SIZE_BYTES);
            await navigator.clipboard.writeText(text);
            result = 'copied';
        } catch (error) {
            result = error instanceof LogTooLargeError ? 'tooLarge' : 'failed';
        }
        if (generation !== copyGeneration.current) {
            return;
        }
        setCopyState(result);
        copyTimer.current = window.setTimeout(() => {
            if (generation === copyGeneration.current) {
                setCopyState('idle');
            }
        }, 2000);
    }, [instanceId, segment.id]);

    const setLevel = (level: LevelFilter) => setOpts((previous) => ({ ...previous, level }));

    return (
        <div className={`LogView${opts.wrap ? '' : ' NoWrap'}`}>
            <div className="LogToolbar">
                <SegmentedFilter<LevelFilter>
                    value={opts.level}
                    onChange={setLevel}
                    options={[
                        { value: 'all', label: 'All' },
                        { value: 'warn', label: 'Warnings' },
                        { value: 'error', label: 'Errors' },
                    ]}
                />
                <span className="Divider" />
                <label>
                    <input
                        type="checkbox"
                        checked={opts.wrap}
                        onChange={(event) =>
                            setOpts((previous) => ({ ...previous, wrap: event.target.checked }))
                        }
                    />
                    Wrap
                </label>
                <label
                    className={live ? undefined : 'Disabled'}
                    title={live ? undefined : 'The step is finished'}
                >
                    <input
                        type="checkbox"
                        checked={opts.follow}
                        disabled={!live}
                        onChange={(event) =>
                            setOpts((previous) => ({ ...previous, follow: event.target.checked }))
                        }
                    />
                    Follow
                </label>
                {ownLog && (
                    <>
                        <span className="Divider" />
                        <button
                            type="button"
                            className={`TextAction Copy-${copyState}`}
                            disabled={copyState === 'copying'}
                            onClick={copyHandler}
                        >
                            <Icon name={COPY_ICONS[copyState]} loading={copyState === 'copying'} />
                            {COPY_LABELS[copyState]}
                        </button>
                    </>
                )}
            </div>

            {rootLog.error && (
                <div className="LogError">
                    <RequestErrorMessage error={rootLog.error} />
                    <button type="button" onClick={() => logs.retry(segment.id)}>
                        Retry
                    </button>
                </div>
            )}

            {rootLog.truncated && (
                <div className="Truncated">
                    Showing the last {Math.round(ROOT_TAIL_BYTES / 1024)} KB of{' '}
                    {rootLog.totalBytes !== undefined
                        ? `${Math.round(rootLog.totalBytes / 1024)} KB`
                        : 'the log'}
                    .{' '}
                    <button type="button" onClick={() => loadFull(segment.id)}>
                        Load full log
                    </button>
                </div>
            )}

            <div className="LogLines">
                {allItems.length > items.length && (
                    <div className="More">
                        <button
                            type="button"
                            onClick={() => setRenderItemLimit((limit) => limit + RENDER_ITEM_PAGE)}
                        >
                            Show up to 2,000 earlier log entries
                        </button>
                    </div>
                )}

                {items.map((item, index) =>
                    item.kind === 'line' ? (
                        <LogLineRow
                            key={`l${item.segment.segment.id}:${item.line.n}`}
                            line={item.line}
                            segmentId={item.segment.segment.id}
                        />
                    ) : (
                        <SegmentHeader
                            key={`h${item.segment.segment.id}:${index}`}
                            node={item.segment}
                            continued={item.continued}
                            log={logs.get(item.segment.segment.id)}
                            lines={linesOf(item.segment.segment.id)}
                            level={opts.level}
                            processStatus={processStatus}
                            onSelect={onSelect}
                            onLoadFull={loadFull}
                            onRetry={logs.retry}
                        />
                    )
                )}

                {hiddenCount > 0 && (
                    <div className="More">
                        {hiddenCount} more nested {hiddenCount === 1 ? 'step is' : 'steps are'} not
                        shown.{' '}
                        <button
                            type="button"
                            onClick={() =>
                                setRequestBudget((budget) => budget + REQUEST_BUDGET_PAGE_BYTES)
                            }
                        >
                            Show more nested steps
                        </button>
                    </div>
                )}

                {rootLog.loaded && items.length === 0 && !rootLog.error && (
                    <div className="Empty">
                        {linesOf(segment.id).length > 0
                            ? 'No lines at this level.'
                            : 'The step has no log output.'}
                    </div>
                )}
                {!rootLog.loaded && <div className="Empty">Loading...</div>}
                <div ref={bottomRef} />
            </div>
        </div>
    );
};

interface FlowTreeNode {
    node: SegmentNode;
    children: FlowTreeNode[];
}

const LogLineRow = React.memo(({ line, segmentId }: { line: LogLine; segmentId: number }) => (
    <div
        className={`LogLine${line.level ? ` Level-${line.level}` : ''}`}
        data-segment={segmentId}
        data-line={line.n}
    >
        <span className="No">{line.n}</span>
        <span className="Ts">{line.ts !== undefined ? formatDate(line.ts, 'HH:mm:ss') : ''}</span>
        <span className="Lvl">{line.ts !== undefined ? line.level : ''}</span>
        <span className="Msg">
            {splitLinks(line.text).map((p, i) =>
                'url' in p ? (
                    <a key={i} href={p.url} target="_blank" rel="noopener noreferrer">
                        {p.url}
                    </a>
                ) : 'instanceId' in p ? (
                    <a
                        key={i}
                        href={`#/process/${p.instanceId}/log-tree`}
                        target="_blank"
                        rel="noopener noreferrer"
                    >
                        {p.instanceId}
                    </a>
                ) : (
                    <React.Fragment key={i}>{p.text}</React.Fragment>
                )
            )}
        </span>
    </div>
));

interface SegmentHeaderProps {
    node: SegmentNode;
    continued: boolean;
    log: SegmentLog;
    lines: LogLine[];
    level: LevelFilter;
    processStatus?: ProcessStatus;
    onSelect: (segmentId: number) => void;
    onLoadFull: (segmentId: number) => void;
    onRetry: (segmentId: number) => void;
}

// the beginning (or the continuation) of the lines of a nested step, a link to the step
const SegmentHeader = ({
    node,
    continued,
    log,
    lines,
    level,
    processStatus,
    onSelect,
    onLoadFull,
    onRetry,
}: SegmentHeaderProps) => {
    const { name, note } = describeNode(node);

    // tell why the step shows no lines: the nested steps below it are not its output
    let empty: string | undefined;
    if (!continued && log.loaded && !log.error) {
        if (lines.length === 0) {
            empty = isLive(node, processStatus) ? 'No output yet.' : 'The step has no log output.';
        } else if (!lines.some((l) => levelOk(l, level))) {
            empty = 'No lines at this level.';
        }
    }
    return (
        // the whole plaque opens the step, "load full" is a separate action
        <div
            className={`SegmentLinkRow${continued ? ' Continued' : ''}`}
            role="link"
            tabIndex={0}
            title="Open the step"
            onClick={() => onSelect(node.segment.id)}
            onKeyDown={(ev) => {
                if (ev.key === 'Enter' || ev.key === ' ') {
                    ev.preventDefault();
                    onSelect(node.segment.id);
                }
            }}
        >
            <SegmentStatusIcon
                status={node.segment.status}
                processStatus={processStatus}
                failedInside={node.hasFailedDescendant}
                retried={node.retried}
                recovered={node.recovered}
            />
            <span className="Name">{name}</span>
            {note && <span className="Note">{note}</span>}
            {continued && <span className="Note">continued</span>}

            <span className="Spacer" />

            {!continued && !log.loaded && <span className="Hint">loading...</span>}
            {empty && <span className="Hint">{empty}</span>}
            {!continued && log.error && (
                <span className="Hint Error" title={log.error.details}>
                    {log.error.message}
                    {' · '}
                    <button
                        type="button"
                        onClick={(event) => {
                            event.stopPropagation();
                            onRetry(node.segment.id);
                        }}
                    >
                        Retry
                    </button>
                </span>
            )}
            {!continued && log.truncated && (
                <span className="Hint">
                    last {Math.round(NESTED_TAIL_BYTES / 1024)} KB ·{' '}
                    <button
                        type="button"
                        onClick={(ev) => {
                            ev.stopPropagation();
                            onLoadFull(node.segment.id);
                        }}
                    >
                        load full
                    </button>
                </span>
            )}
            <Icon name="chevron right" className="Go" />
        </div>
    );
};

export default LogView;
