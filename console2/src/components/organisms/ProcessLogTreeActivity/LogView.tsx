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
import * as React from 'react';
import { useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Icon, SemanticICONS } from 'semantic-ui-react';
import { format as formatDate, parseISO as parseDate } from 'date-fns';

import type { ConcordId } from '../../../api/common';
import { copyTextToClipboard } from '../../../utils';
import { PollingErrorContext } from '../../../api/usePolling';
import { isFinal } from '../../../api/process';
import type { ProcessStatus } from '../../../api/process';
import { getFullSegmentLog, LogTooLargeError, SegmentStatus } from '../../../api/process/log';
import RequestErrorMessage from '../../molecules/RequestErrorMessage';
import { SegmentStatusIcon } from './LogSegmentTree';
import { buildLogFlow, parseLogWindow, splitLinks } from './logParser';
import type { FlowItem, FlowSegment, LogLine, ParsedLog } from './logParser';
import { describeNode } from './segmentTree';
import type { SegmentNode } from './segmentTree';

import { useSegmentLogs } from './useSegmentLogs';
import type { SegmentLog, SegmentLogRequest, SegmentLogs } from './useSegmentLogs';

import KindBadge from './KindBadge';

import './LogView.css';

const LOG_SEGMENTS_PAGE = 100;

const ROOT_TAIL_BYTES = 256 * 1024;
const NESTED_TAIL_BYTES = 64 * 1024;
const LINES_PAGE = 200;
const MAX_COPY_SIZE_BYTES = 5 * 1024 * 1024;
const OPTS_KEY = 'logTree.logOpts';

interface LogOpts {
    wrap: boolean;
    follow: boolean;
}

const DEFAULT_OPTS: LogOpts = { wrap: true, follow: true };

const loadOpts = (): LogOpts => {
    try {
        const saved = JSON.parse(localStorage.getItem(OPTS_KEY) ?? '{}');
        return {
            wrap: typeof saved.wrap === 'boolean' ? saved.wrap : DEFAULT_OPTS.wrap,
            follow: typeof saved.follow === 'boolean' ? saved.follow : DEFAULT_OPTS.follow,
        };
    } catch {
        return DEFAULT_OPTS;
    }
};

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
    // The system segment has no status of its own.
    (node.segment.id === 0 || node.segment.status === SegmentStatus.RUNNING) &&
    !isFinal(processStatus);

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
        refreshToken: `${forceRefresh}:${processStatus}`,
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
    const reportPollingError = useContext(PollingErrorContext);
    const [historyPaused, setHistoryPaused] = useState(false);
    const follow = opts.follow && !historyPaused;
    const [lineLimits, setLineLimits] = useState<Record<number, number>>({});
    const descendants = useMemo(() => {
        const result: SegmentNode[] = [];
        const collect = (current: SegmentNode) => {
            current.children.forEach((child) => {
                result.push(child);
                collect(child);
            });
        };
        collect(node);
        return result.sort((a, b) => a.segment.id - b.segment.id);
    }, [node]);
    // Keep loaded history while live updates append new descendants.
    const [oldestId, setOldestId] = useState(
        () =>
            descendants[Math.max(0, descendants.length - LOG_SEGMENTS_PAGE)]?.segment.id ??
            segment.id
    );
    const hasPrevious = descendants.some((n) => n.segment.id < oldestId);
    const previous = () => {
        const older = descendants.filter((n) => n.segment.id < oldestId);
        setOldestId(older[Math.max(0, older.length - LOG_SEGMENTS_PAGE)]?.segment.id ?? oldestId);
    };
    const scrollRef = useRef<HTMLDivElement>(null);
    const anchor = useRef<{ key: string; top: number }>();
    const rememberPosition = useCallback(() => {
        const container = scrollRef.current;
        if (!container) return;
        const top = container.getBoundingClientRect().top;
        const row = [...container.querySelectorAll<HTMLElement>('.LogLine[data-anchor]')].find(
            (el) => el.getBoundingClientRect().bottom > top
        );
        anchor.current = row
            ? { key: row.dataset.anchor!, top: row.getBoundingClientRect().top - top }
            : undefined;
    }, []);
    useLayoutEffect(() => {
        const container = scrollRef.current;
        if (container && anchor.current && !follow) {
            const saved = anchor.current;
            const row = [...container.querySelectorAll<HTMLElement>('.LogLine[data-anchor]')].find(
                (el) => el.dataset.anchor === saved.key
            );
            if (row)
                container.scrollTop +=
                    row.getBoundingClientRect().top -
                    container.getBoundingClientRect().top -
                    saved.top;
        }
        rememberPosition();
    });
    const [copyState, setCopyState] = useState<CopyState>('idle');
    const bottomRef = useRef<HTMLDivElement>(null);
    const parsed = useRef(
        new Map<number, { parsed: ParsedLog; loaded: boolean }>()
    );
    const appendedWhileReading = useRef(new Map<number, number>());
    if (follow) appendedWhileReading.current.clear();
    const lineLimit = (id: number) =>
        (lineLimits[id] ?? LINES_PAGE) + (appendedWhileReading.current.get(id) ?? 0);
    const copyGeneration = useRef(0);
    const copyTimer = useRef<number>();

    const { tree, included } = useMemo(() => {
        const includedIds = new Set([
            segment.id,
            ...descendants.filter((n) => n.segment.id >= oldestId).map((n) => n.segment.id),
        ]);
        const included: SegmentNode[] = [];
        // Preserve the hierarchy, but only page members produce log blocks.
        const build = (current: SegmentNode): FlowTreeNode[] => {
            const children = current.children.flatMap(build);
            if (!includedIds.has(current.segment.id)) return children;
            included.push(current);
            return [{ node: current, children }];
        };
        const tree = build(node)[0];
        // Fetch the selected step first, then descendants in creation order.
        included.sort((a, b) => a.segment.id - b.segment.id);
        return { tree, included };
    }, [descendants, oldestId, node, segment.id]);

    const requests = useMemo(
        (): SegmentLogRequest[] =>
            included.map((current) => ({
                id: current.segment.id,
                live: isLive(current, processStatus),
                tailBytes: current.segment.id === segment.id ? ROOT_TAIL_BYTES : NESTED_TAIL_BYTES,
            })),
        [included, segment.id, processStatus]
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
        const log = logs.get(id);
        const cached = parsed.current.get(id);
        const next = parseLogWindow(log.text, log.before, log.after, cached?.parsed);
        if (cached && cached.parsed === next && cached.loaded === log.loaded) {
            return next.lines;
        }
        // Keep the start of the visible window fixed while the user reads history.
        // An append adds rows at the bottom; it must not evict earlier visible rows.
        if (!follow && cached?.loaded && log.after > cached.parsed.after) {
            appendedWhileReading.current.set(
                id,
                (appendedWhileReading.current.get(id) ?? 0) +
                    Math.max(0, next.lines.length - cached.parsed.lines.length)
            );
        }
        parsed.current.set(id, {
            parsed: next,
            loaded: log.loaded,
        });
        return next.lines;
    };

    const toFlow = (current: FlowTreeNode): FlowSegment<SegmentNode> => ({
        segment: current.node,
        start: parseDate(current.node.segment.createdAt).getTime(),
        lines: linesOf(current.node.segment.id).slice(-lineLimit(current.node.segment.id)),
        children: current.children.map(toFlow),
    });
    const items = buildLogFlow(toFlow(tree));
    // A header is sticky only within its uninterrupted run of log lines.
    const blocks: {
        header?: Extract<FlowItem<SegmentNode>, { kind: 'header' }>;
        lines: Extract<FlowItem<SegmentNode>, { kind: 'line' }>[];
    }[] = [];
    for (const item of items) {
        if (item.kind === 'header') {
            blocks.push({ header: item, lines: [] });
        } else {
            if (blocks.length === 0) blocks.push({ lines: [] });
            blocks[blocks.length - 1].lines.push(item);
        }
    }
    const rootLog: SegmentLog = logs.get(segment.id);
    const [initialReady, setInitialReady] = useState(false);
    const initialPending = included.some((current) => {
        const log = logs.get(current.segment.id);
        return !log.loaded && !log.error;
    });
    useEffect(() => {
        if (!initialPending) setInitialReady(true);
    }, [initialPending]);
    const initialLoading = !initialReady && initialPending;
    const hasPreviousLines = (id: number) =>
        logs.get(id).truncated || linesOf(id).length > lineLimit(id);
    const loadPreviousLines = (id: number) => {
        rememberPosition();
        setHistoryPaused(true);
        const limit = lineLimit(id);
        setLineLimits((previous) => ({
            ...previous,
            [id]: (previous[id] ?? LINES_PAGE) + LINES_PAGE,
        }));
        if (linesOf(id).length <= limit) logs.previous(id);
    };
    const live = isLive(node, processStatus);
    const contentRevision = included.reduce(
        (total, current) => total + logs.get(current.segment.id).revision,
        0
    );

    useEffect(() => {
        if (live && follow && bottomRef.current) {
            bottomRef.current.scrollIntoView({ block: 'end' });
        }
    }, [live, follow, contentRevision]);

    const copyHandler = useCallback(async () => {
        const generation = ++copyGeneration.current;
        setCopyState('copying');
        let result: CopyState;
        try {
            const text = await getFullSegmentLog(instanceId, segment.id, MAX_COPY_SIZE_BYTES);
            result = (await copyTextToClipboard(text)) ? 'copied' : 'failed';
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

    return (
        <div className={`LogView${opts.wrap ? '' : ' NoWrap'}`}>
            <div className="LogToolbar">
                <label>
                    <input
                        type="checkbox"
                        checked={opts.wrap}
                        onChange={(event) =>
                            setOpts((previous) => ({
                                ...previous,
                                wrap: event.target.checked,
                            }))
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
                        checked={follow}
                        disabled={!live}
                        onChange={(event) => {
                            setHistoryPaused(false);
                            setOpts((previous) => ({
                                ...previous,
                                follow: event.target.checked,
                            }));
                        }}
                    />
                    Auto-scroll
                </label>
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
            </div>

            {logs.pollingError && !reportPollingError && (
                <div className="LogError">
                    <RequestErrorMessage error={logs.pollingError} />
                </div>
            )}
            {rootLog.error && !rootLog.error.retrying && (
                <div className="LogError">
                    <RequestErrorMessage error={rootLog.error} />
                    <button type="button" onClick={() => logs.retry(segment.id)}>
                        Retry
                    </button>
                </div>
            )}

            <div className="LogLines" ref={scrollRef} onScroll={rememberPosition}>
                <div className="LogContent">
                    {initialLoading ? (
                        <div className="Empty">Loading...</div>
                    ) : (
                        <>
                            {hasPrevious && (
                                <div className="More">
                                    <span>Latest {included.length - 1} nested steps shown.</span>
                                    <button
                                        type="button"
                                        onClick={() => {
                                            rememberPosition();
                                            setHistoryPaused(true);
                                            previous();
                                        }}
                                    >
                                        Load earlier steps
                                    </button>
                                </div>
                            )}
                            {hasPreviousLines(segment.id) && (
                                <LogHistoryNotice
                                    log={rootLog}
                                    onLoad={() => loadPreviousLines(segment.id)}
                                />
                            )}
                            {blocks.map((block, index) => (
                                <section
                                    className={`LogBlock${block.header ? ' HasHeader' : ''}${
                                        block.header?.continued ? ' Continued' : ''
                                    }`}
                                    key={
                                        block.header
                                            ? `h${block.header.segment.segment.id}:${index}`
                                            : 'own-log'
                                    }
                                >
                                    {block.header && (
                                        <SegmentHeader
                                            node={block.header.segment}
                                            continued={block.header.continued}
                                            current={block.header.segment.segment.id === segment.id}
                                            log={logs.get(block.header.segment.segment.id)}
                                            lines={linesOf(block.header.segment.segment.id)}
                                            processStatus={processStatus}
                                            onSelect={onSelect}
                                            onLoadPrevious={loadPreviousLines}
                                            hasPrevious={hasPreviousLines(
                                                block.header.segment.segment.id
                                            )}
                                            onRetry={logs.retry}
                                        />
                                    )}
                                    {block.lines.map((item) => (
                                        <LogLineRow
                                            key={`l${item.segment.segment.id}:${item.line.offset}`}
                                            line={item.line}
                                            segmentId={item.segment.segment.id}
                                        />
                                    ))}
                                </section>
                            ))}

                            {rootLog.loaded && items.length === 0 && !rootLog.error && (
                                <div className="Empty">The step has no log output.</div>
                            )}
                            {!rootLog.loaded && !rootLog.error && (
                                <div className="Empty">Loading...</div>
                            )}
                        </>
                    )}
                    <div ref={bottomRef} />
                </div>
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
        data-anchor={`l${segmentId}:${line.offset}`}
    >
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
    // the selected step, it has the header above the log
    current: boolean;
    log: SegmentLog;
    lines: LogLine[];
    processStatus?: ProcessStatus;
    onSelect: (segmentId: number) => void;
    onLoadPrevious: (segmentId: number) => void;
    hasPrevious: boolean;
    onRetry: (segmentId: number) => void;
}

// the beginning (or the continuation) of the lines of a nested step, a link to the step
const SegmentHeader = ({
    node,
    continued,
    current,
    log,
    lines,
    processStatus,
    onSelect,
    onLoadPrevious,
    hasPrevious,
    onRetry,
}: SegmentHeaderProps) => {
    const { kind, name, note, badgeOnly, groupKind } = describeNode(node);

    // the parent's lines resume after a nested step: just the parent's name
    if (continued) {
        const text = badgeOnly ? kind : name;
        if (current) {
            return (
                <div
                    className="SegmentLinkRow Continued Current"
                    title={`The log of ${text} continues`}
                >
                    <span className="Name">
                        <SegmentName name={text ?? name} />
                    </span>
                </div>
            );
        }
        return (
            <div
                className="SegmentLinkRow Continued"
                role="link"
                tabIndex={0}
                title={`The log of ${text} continues. Open the step`}
                onClick={() => onSelect(node.segment.id)}
                onKeyDown={(ev) => {
                    if (ev.target !== ev.currentTarget) return;
                    if (ev.key === 'Enter' || ev.key === ' ') {
                        ev.preventDefault();
                        onSelect(node.segment.id);
                    }
                }}
            >
                <span className="Name">
                    <SegmentName name={text ?? name} />
                </span>
                <Icon name="chevron right" className="Go" />
            </div>
        );
    }

    // tell why the step shows no lines: the nested steps below it are not its output
    let empty: string | undefined;
    if (log.loaded && !log.error) {
        if (lines.length === 0) {
            empty = isLive(node, processStatus) ? 'No output yet.' : 'The step has no log output.';
        }
    }

    return (
        <>
            <div
                className="SegmentLinkRow"
                data-anchor={`h${node.segment.id}`}
                role="link"
                tabIndex={0}
                title="Open the step"
                onClick={() => onSelect(node.segment.id)}
                onKeyDown={(ev) => {
                    if (ev.target !== ev.currentTarget) return;
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
                />
                {groupKind && <KindBadge kind={groupKind} />}
                {kind && <KindBadge kind={kind} title={badgeOnly ? name : undefined} />}
                {!badgeOnly && (
                    <span className="Name" title={name}>
                        <SegmentName name={name} />
                    </span>
                )}
                <Icon name="chevron right" className="Go" />
                {note && <span className="Note">{note}</span>}

                <span className="Spacer" />

                {!log.loaded && !log.error && <span className="Hint">loading...</span>}
                {empty && <span className="Hint">{empty}</span>}
            </div>
            {log.error && !log.error.retrying && (
                <div className="LogError">
                    <RequestErrorMessage error={log.error} />
                    <button type="button" onClick={() => onRetry(node.segment.id)}>
                        Retry
                    </button>
                </div>
            )}
            {hasPrevious && (
                <LogHistoryNotice log={log} onLoad={() => onLoadPrevious(node.segment.id)} />
            )}
        </>
    );
};

const LogHistoryNotice = ({ log, onLoad }: { log: SegmentLog; onLoad: () => void }) => (
    <div className="Truncated">
        <span>Showing only the last part of this step’s log.</span>
        <button type="button" disabled={log.loadingPrevious} onClick={onLoad}>
            {log.loadingPrevious ? 'Loading...' : 'Load earlier lines'}
        </button>
    </div>
);

export default LogView;
