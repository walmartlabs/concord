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
import { resolveStepRun } from './treeApi';
import { toRequestError, RequestErrorData } from '../../../api/common';
import { LogSegmentEntry } from '../../../api/process/log';
import * as React from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Icon, Modal } from 'semantic-ui-react';
import { format as formatDate, parseISO as parseDate } from 'date-fns';

import { ConcordId } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { ProcessElementEvent, ProcessEventEntry } from '../../../api/process/event';
import CallData from './CallData';
import KindBadge from './KindBadge';
import { SegmentStatusIcon } from './LogSegmentTree';
import { describeNode, SegmentNode } from './segmentTree';
import { useStepEvents } from './useStepEvents';

import './StepHeader.css';

interface Props {
    instanceId: ConcordId;
    node: SegmentNode;
    processStatus?: ProcessStatus;
}

// the "post" event has the error, fall back to "pre" while the step is running
const pickEvent = (events: ProcessEventEntry<ProcessElementEvent>[]) =>
    (events.find((e) => e.data.phase === 'post') ?? events[0])?.data;

/**
 * The segment whose events describe the node: events are linked to the innermost segment
 * of the step, i.e. the last attempt of a retry or the first iteration of a loop
 * (all iterations are the same step). An "error" block is not a step.
 */
const stepRunOf = (node: SegmentNode): SegmentNode | undefined => {
    if (node.kind === 'error') {
        return undefined;
    }
    const runs = node.children.filter(
        (c) =>
            (c.kind === 'attempt' || c.kind === 'iteration') &&
            c.segment.correlationId === node.segment.correlationId
    );
    if (runs.length === 0) {
        return node;
    }
    return stepRunOf(runs[0].kind === 'attempt' ? runs[runs.length - 1] : runs[0]);
};

const threadOf = (node: SegmentNode): string =>
    node.segment.threadId === undefined ? 'main' : String(node.segment.threadId);

const MetaRow = ({
    label,
    mono,
    children,
}: {
    label: string;
    mono?: boolean;
    children: React.ReactNode;
}) => (
    <div className="MetaRow">
        <span className="MetaLabel">{label}</span>
        <span className={`MetaValue${mono ? ' Mono' : ''}`}>{children}</span>
    </div>
);

const StepHeader = ({ instanceId, node, processStatus }: Props) => {
    const s = node.segment;
    const [infoOpen, setInfoOpen] = useState<boolean>(false);
    const [runState, setRunState] =
        useState<{ id: number; run?: LogSegmentEntry; error?: RequestErrorData }>();
    const [runRefresh, setRunRefresh] = useState(0);
    useEffect(() => {
        if (!node.remote || !infoOpen || node.kind === 'error') return;
        const controller = new AbortController();
        setRunState({ id: s.id });
        resolveStepRun(instanceId, s.id, controller.signal).then(
            (run) => {
                if (!controller.signal.aborted) setRunState({ id: s.id, run });
            },
            (error) => {
                if (!controller.signal.aborted)
                    setRunState({ id: s.id, error: toRequestError(error) });
            }
        );
        return () => controller.abort();
    }, [instanceId, s.id, s.status, !!node.remote, node.kind, infoOpen, runRefresh]);
    const run = node.remote
        ? runState?.id === s.id
            ? runState.run
            : undefined
        : stepRunOf(node)?.segment;
    const {
        events,
        error: eventError,
        exhausted: eventsExhausted,
        refresh: refreshEvents,
    } = useStepEvents(instanceId, run, {
        includeAll: false,
        enabled: true,
        waitForPost: false,
        processStatus,
    });
    const event = events ? pickEvent(events) : undefined;
    const detailsError = (runState?.id === s.id ? runState.error : undefined) ?? eventError;
    const refreshDetails = () => {
        setRunRefresh((n) => n + 1);
        refreshEvents();
    };
    const [linkCopied, setLinkCopied] = useState<boolean>(false);
    const linkTimeout = useRef<number | undefined>(undefined);

    // location, flow and error of the step come from the ELEMENT events of its segment

    useEffect(() => () => window.clearTimeout(linkTimeout.current), []);

    // another step is selected
    useEffect(() => setInfoOpen(false), [s.id]);

    const linkHandler = useCallback(async () => {
        const url = `${window.location.origin}${window.location.pathname}#/process/${instanceId}/log-tree#step=${s.id}`;
        try {
            await navigator.clipboard.writeText(url);
            setLinkCopied(true);
            window.clearTimeout(linkTimeout.current);
            linkTimeout.current = window.setTimeout(() => setLinkCopied(false), 1500);
        } catch {
            // ignore, the link is also in the address bar
        }
    }, [instanceId, s.id]);

    const { kind, name, note, badgeOnly, loop } = describeNode(node);
    // only task calls record the input/output ("phase" is set by the task call events only)
    const taskCall = event?.phase !== undefined;

    const createdAt = parseDate(s.createdAt);

    return (
        <div className="StepHeader">
            <div className="Title">
                <SegmentStatusIcon
                    status={s.status}
                    processStatus={processStatus}
                    failedInside={false}
                    retried={node.retried}
                />
                {loop && <KindBadge kind="loop" />}
                {kind && <KindBadge kind={kind} title={badgeOnly ? name : undefined} />}
                {!badgeOnly && (
                    <span className="Name" title={name}>
                        <SegmentName name={name} />
                    </span>
                )}
                {note && <span className={`Note${node.retried ? ' Retried' : ''}`}>{note}</span>}

                <div className="Actions">
                    <button
                        type="button"
                        className="IconAction"
                        aria-label="Step details"
                        data-tooltip="Step details"
                        data-position="bottom right"
                        data-inverted=""
                        onClick={() => {
                            refreshDetails();
                            setInfoOpen(true);
                        }}
                    >
                        <Icon name="info circle" />
                    </button>
                    <button
                        type="button"
                        className={`IconAction${linkCopied ? ' Active' : ''}`}
                        aria-label="Copy link to this step"
                        data-tooltip={linkCopied ? 'Copied!' : 'Copy link to this step'}
                        data-position="bottom right"
                        data-inverted=""
                        onClick={linkHandler}
                    >
                        <Icon name={linkCopied ? 'check' : 'linkify'} />
                    </button>
                </div>
            </div>

            <Modal open={infoOpen} onClose={() => setInfoOpen(false)} size="small">
                <Modal.Header className="StepInfoHeader">
                    <SegmentStatusIcon
                        status={s.status}
                        processStatus={processStatus}
                        failedInside={false}
                        retried={node.retried}
                    />
                    <div className="HeaderText">
                        <div className="HeaderTitle">
                            {loop && <KindBadge kind="loop" />}
                            {kind && <KindBadge kind={kind} />}
                            <SegmentName name={name} />
                            {note && <span className="Note">{note}</span>}
                        </div>
                    </div>
                </Modal.Header>
                <Modal.Content scrolling={true} className="StepInfo">
                    <div className="Meta">
                        <MetaRow label="Started">
                            {formatDate(createdAt, 'yyyy-MM-dd HH:mm:ss')}
                        </MetaRow>
                        {event?.processDefinitionId && (
                            <MetaRow label="Flow" mono={true}>
                                {event.processDefinitionId}
                            </MetaRow>
                        )}
                        <MetaRow label="Thread">{threadOf(node)}</MetaRow>
                        {event?.fileName && (
                            <MetaRow label="Source" mono={true}>
                                {event.fileName}:{event.line}
                            </MetaRow>
                        )}
                    </div>
                    {detailsError && (
                        <div className="EventState" role="alert">
                            Step details are not available: {detailsError.message}
                            {detailsError.details && (
                                <span className="Details"> {detailsError.details}</span>
                            )}
                            <button type="button" onClick={refreshDetails}>
                                Retry
                            </button>
                        </div>
                    )}
                    {eventsExhausted && !event && (
                        <div className="EventState">
                            Step details have not arrived yet.
                            <button type="button" onClick={refreshDetails}>
                                Refresh details
                            </button>
                        </div>
                    )}
                    {taskCall && run && (
                        <CallData instanceId={instanceId} run={run} processStatus={processStatus} />
                    )}
                </Modal.Content>
            </Modal>
        </div>
    );
};

export default StepHeader;
