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
import { useCallback, useEffect, useRef, useState } from 'react';
import { Icon, Modal } from 'semantic-ui-react';
import { format as formatDate, parseISO as parseDate } from 'date-fns';

import { ConcordId } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import {
    listStepEvents as apiListStepEvents,
    ProcessElementEvent,
    ProcessEventEntry,
} from '../../../api/process/event';
import CallData from './CallData';
import { SegmentStatusIcon } from './LogSegmentTree';
import { describeNode, isGroup, SegmentNode } from './segmentTree';

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
 * The segment whose events describe the node: the node itself, the last attempt of a retry group
 * or the first iteration of a loop (all iterations are the same step). Other groups are not a step.
 */
const stepRunOf = (node: SegmentNode): SegmentNode | undefined => {
    switch (node.kind) {
        case 'segment':
            return node;
        case 'retry':
            return node.children[node.children.length - 1];
        case 'loop':
            return node.children[0] ? stepRunOf(node.children[0]) : undefined;
        default:
            return undefined;
    }
};

// the real segments of the node: the node itself or the steps of a group
const realSegments = (node: SegmentNode): SegmentNode[] =>
    isGroup(node) ? node.children.flatMap(realSegments) : [node];

const threadOf = (node: SegmentNode): string => {
    const threads = new Set(realSegments(node).map((n) => n.segment.threadId));
    if (threads.size > 1) {
        return 'several';
    }
    const [thread] = [...threads];
    return thread === undefined ? 'main' : String(thread);
};

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
    const [event, setEvent] = useState<ProcessElementEvent>();
    const [infoOpen, setInfoOpen] = useState<boolean>(false);
    const [linkCopied, setLinkCopied] = useState<boolean>(false);
    const linkTimeout = useRef<number | undefined>(undefined);

    // location, flow and error of the step come from the ELEMENT events of its segment
    const run = stepRunOf(node)?.segment;
    useEffect(() => {
        setEvent(undefined);
        if (!run?.correlationId) {
            return;
        }

        let cancelled = false;
        apiListStepEvents<ProcessElementEvent>(instanceId, {
            segmentId: run.id,
            correlationId: run.correlationId,
            repeated: run.attempt !== undefined || run.loopIndex !== undefined,
        })
            .then((events) => {
                if (!cancelled) {
                    setEvent(pickEvent(events));
                }
            })
            .catch(() => {
                // the header works without the event details
            });

        return () => {
            cancelled = true;
        };
    }, [instanceId, run?.id, run?.correlationId, run?.attempt, run?.loopIndex, run?.status]);

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

    const { name, note } = describeNode(node);
    const group = isGroup(node);
    // only task calls record the input/output ("phase" is set by the task call events only)
    const taskCall = !group && event?.phase !== undefined;

    const createdAt = parseDate(s.createdAt);

    return (
        <div className="StepHeader">
            <div className="Title">
                <SegmentStatusIcon
                    status={s.status}
                    processStatus={processStatus}
                    failedInside={false}
                    retried={node.retried}
                    recovered={node.recovered}
                />
                <span className="Name" title={name}>
                    {name}
                </span>
                {note && <span className={`Note${node.retried ? ' Retried' : ''}`}>{note}</span>}

                <div className="Actions">
                    <button
                        type="button"
                        className="IconAction"
                        aria-label="Step details"
                        data-tooltip="Step details"
                        data-position="bottom right"
                        data-inverted=""
                        onClick={() => setInfoOpen(true)}
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
                        recovered={node.recovered}
                    />
                    <div className="HeaderText">
                        <div className="HeaderTitle">
                            {name}
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
                    {taskCall && run && <CallData instanceId={instanceId} run={run} />}
                </Modal.Content>
            </Modal>
        </div>
    );
};

export default StepHeader;
