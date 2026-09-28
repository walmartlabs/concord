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

import { ConcordId } from '../../../api/common';
import { isFinal, ProcessStatus } from '../../../api/process';
import { getFullSegmentLog, LogTooLargeError, SegmentStatus } from '../../../api/process/log';
import { SegmentStatusIcon } from './LogSegmentTree';
import { buildLogFlow, FlowSegment, LogLevel, LogLine, parseLog, splitLinks } from './logParser';
import { describeNode, isGroup, SegmentNode } from './segmentTree';

import { SegmentLog, SegmentLogRequest, useSegmentLogs } from './useSegmentLogs';

import SegmentedFilter from './SegmentedFilter';

import './LogView.css';

// how much of the logs to load at first, the rest is loaded on demand
const TAIL_BYTES = 1024 * 1024;
const NESTED_TAIL_BYTES = 256 * 1024;
// nested steps shown at once (e.g. loops can have thousands of iterations)
const NESTED_PAGE = 100;
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
    node.children.flatMap((c) => (isGroup(c) ? nestedSegments(c) : [c]));

interface Props {
    instanceId: ConcordId;
    node: SegmentNode;
    processStatus?: ProcessStatus;
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

const isLive = (n: SegmentNode, processStatus?: ProcessStatus) =>
    n.segment.status === SegmentStatus.RUNNING && !isFinal(processStatus);

const LogView = ({ instanceId, node, processStatus, onSelect }: Props) => {
    const s = node.segment;
    const [fullIds, setFullIds] = useState<ReadonlySet<number>>(new Set());
    const [nestedLimit, setNestedLimit] = useState(NESTED_PAGE);
    const [opts, setOpts] = useState<LogOpts>(loadOpts);
    const [copyState, setCopyState] = useState<CopyState>('idle');
    const bottomRef = useRef<HTMLDivElement>(null);
    const parsed = useRef(new Map<number, { text: string; lines: LogLine[] }>());

    useEffect(() => {
        localStorage.setItem(OPTS_KEY, JSON.stringify(opts));
    }, [opts]);

    // the selected step and its nested steps (up to the limit), in the tree order
    const { tree, included, hiddenCount } = useMemo(() => {
        let budget = nestedLimit;
        let total = 0;
        const included: SegmentNode[] = [node];
        const build = (n: SegmentNode): FlowTreeNode => {
            const nested = nestedSegments(n);
            total += nested.length;
            const shown = nested.slice(0, Math.max(0, budget));
            budget -= shown.length;
            included.push(...shown);
            return { node: n, children: shown.map(build) };
        };
        const tree = build(node);
        return { tree, included, hiddenCount: total - (included.length - 1) };
    }, [node, nestedLimit]);

    // groups (retry attempts, loop iterations, error handlers) have no log of their own
    const ownLog = !isGroup(node);

    const requests = useMemo(
        (): SegmentLogRequest[] =>
            included
                .filter((n) => !isGroup(n))
                .map((n) => ({
                    id: n.segment.id,
                    live: isLive(n, processStatus),
                    tailBytes: n === node ? TAIL_BYTES : NESTED_TAIL_BYTES,
                    full: fullIds.has(n.segment.id),
                })),
        [included, node, processStatus, fullIds]
    );
    const logOf = useSegmentLogs(instanceId, requests);

    const linesOf = (id: number): LogLine[] => {
        const text = logOf(id).text;
        const cached = parsed.current.get(id);
        if (cached && cached.text === text) {
            return cached.lines;
        }
        const lines = parseLog(text);
        parsed.current.set(id, { text, lines });
        return lines;
    };

    const toFlow = (t: FlowTreeNode): FlowSegment<SegmentNode> => ({
        segment: t.node,
        start: parseDate(t.node.segment.createdAt).getTime(),
        lines: linesOf(t.node.segment.id).filter((l) => levelOk(l, opts.level)),
        children: t.children.map(toFlow),
    });
    const items = buildLogFlow(toFlow(tree));

    const rootLog: SegmentLog = ownLog ? logOf(s.id) : { text: '', truncated: false, loaded: true };
    const live = isLive(node, processStatus);
    const loadedBytes = included.reduce((acc, n) => acc + logOf(n.segment.id).text.length, 0);

    // follow the running log
    useEffect(() => {
        if (live && opts.follow && bottomRef.current) {
            bottomRef.current.scrollIntoView({ block: 'end' });
        }
    }, [live, opts.follow, loadedBytes]);

    const loadFull = useCallback((id: number) => {
        setFullIds((prev) => new Set([...prev, id]));
    }, []);

    const copyHandler = useCallback(async () => {
        setCopyState('copying');
        let result: CopyState;
        try {
            const text = await getFullSegmentLog(instanceId, s.id, MAX_COPY_SIZE_BYTES);
            await navigator.clipboard.writeText(text);
            result = 'copied';
        } catch (e) {
            result = e instanceof LogTooLargeError ? 'tooLarge' : 'failed';
        }
        setCopyState(result);
        window.setTimeout(() => setCopyState('idle'), 2000);
    }, [instanceId, s.id]);

    const setLevel = (level: LevelFilter) => setOpts((prev) => ({ ...prev, level }));

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
                        onChange={(ev) => setOpts((prev) => ({ ...prev, wrap: ev.target.checked }))}
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
                        onChange={(ev) =>
                            setOpts((prev) => ({ ...prev, follow: ev.target.checked }))
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

            {rootLog.truncated && (
                <div className="Truncated">
                    Showing the last {Math.round(TAIL_BYTES / 1024)} KB of{' '}
                    {rootLog.totalBytes !== undefined
                        ? `${Math.round(rootLog.totalBytes / 1024)} KB`
                        : 'the log'}
                    .{' '}
                    <button type="button" onClick={() => loadFull(s.id)}>
                        Load full log
                    </button>
                </div>
            )}

            <div className="LogLines">
                {items.map((item, idx) =>
                    item.kind === 'line' ? (
                        <LogLineRow
                            key={`l${item.segment.segment.id}:${item.line.n}`}
                            line={item.line}
                            segmentId={item.segment.segment.id}
                        />
                    ) : (
                        <SegmentHeader
                            key={`h${item.segment.segment.id}:${idx}`}
                            node={item.segment}
                            continued={item.continued}
                            log={logOf(item.segment.segment.id)}
                            lines={linesOf(item.segment.segment.id)}
                            level={opts.level}
                            processStatus={processStatus}
                            onSelect={onSelect}
                            onLoadFull={loadFull}
                        />
                    )
                )}

                {hiddenCount > 0 && (
                    <div className="More">
                        {hiddenCount} more nested {hiddenCount === 1 ? 'step is' : 'steps are'} not
                        shown.{' '}
                        <button
                            type="button"
                            onClick={() => setNestedLimit((l) => l + NESTED_PAGE)}
                        >
                            Show {Math.min(hiddenCount, NESTED_PAGE)} more
                        </button>
                    </div>
                )}

                {rootLog.loaded && items.length === 0 && (
                    <div className="Empty">
                        {rootLog.error
                            ? `Failed to load the log: ${rootLog.error}`
                            : linesOf(s.id).length > 0
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
            />
            <span className="Name">{name}</span>
            {note && <span className="Note">{note}</span>}
            {continued && <span className="Note">continued</span>}

            <span className="Spacer" />

            {!continued && !log.loaded && <span className="Hint">loading...</span>}
            {empty && <span className="Hint">{empty}</span>}
            {!continued && log.error && <span className="Hint Error">{log.error}</span>}
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
