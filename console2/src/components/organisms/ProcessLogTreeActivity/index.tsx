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
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import type { ConcordId } from '../../../api/common';
import { isFinal, ProcessStatus } from '../../../api/process';
import { listLogSegments as apiListLogSegments } from '../../../api/process/log';
import type { LogSegmentEntry } from '../../../api/process/log';
import { usePolling } from '../../../api/usePolling';
import RequestErrorActivity from '../RequestErrorActivity';
import RequestErrorMessage from '../../molecules/RequestErrorMessage';
import LogSegmentTree from './LogSegmentTree';
import LogView from './LogView';
import StepHeader from './StepHeader';
import { buildSegmentTree, pickDefaultSegment } from './segmentTree';

import './styles.css';

const MIN_HEIGHT = 300;

interface ExternalProps {
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
    onRefresh: () => void;
    dataFetchInterval: number;
}

// "#step=N" rather than "#segmentId=N": LogSegment scrolls the page to the linked segment
const getLinkedStepId = (hash: string): number | undefined => {
    const step = new URLSearchParams(hash.substring(hash.lastIndexOf('#') + 1)).get('step');
    if (!step) {
        return undefined;
    }
    const parsed = Number(step);
    return Number.isFinite(parsed) ? parsed : undefined;
};

const ProcessLogTreeActivityView = ({
    instanceId,
    processStatus,
    loadingHandler,
    forceRefresh,
    onRefresh,
    dataFetchInterval,
}: ExternalProps) => {
    const navigate = useNavigate();
    const location = useLocation();
    const [segments, setSegments] = useState<LogSegmentEntry[]>([]);
    const [selectedId, setSelectedId] = useState<number | undefined>(
        getLinkedStepId(location.hash)
    );
    // until the user picks a step, keep following the "most interesting" one
    const [autoSelect, setAutoSelect] = useState<boolean>(
        getLinkedStepId(location.hash) === undefined
    );
    const [completed, setCompleted] = useState(false);

    // the page doesn't scroll: the layout takes the rest of the window and both panes scroll inside
    const layoutRef = useRef<HTMLDivElement>(null);
    const [height, setHeight] = useState<number>();
    useLayoutEffect(() => {
        window.scrollTo(0, 0);

        const update = () => {
            const el = layoutRef.current;
            if (!el) {
                return;
            }
            const rect = el.getBoundingClientRect();
            const top = rect.top + window.scrollY;
            // paddings of the page containers below the layout
            const page = document.getElementById('root')?.firstElementChild;
            const below = page ? Math.max(0, page.getBoundingClientRect().bottom - rect.bottom) : 0;
            setHeight(
                Math.max(MIN_HEIGHT, Math.floor(window.innerHeight - Math.ceil(top) - below))
            );
        };
        update();

        // the header above can change its size (e.g. once the process info is loaded)
        const observer = new ResizeObserver(() => window.requestAnimationFrame(update));
        observer.observe(document.body);
        if (layoutRef.current?.parentElement) {
            observer.observe(layoutRef.current.parentElement);
        }
        window.addEventListener('resize', update);
        return () => {
            observer.disconnect();
            window.removeEventListener('resize', update);
        };
    }, []);

    const fetchSegments = useCallback(async () => {
        const segments = await apiListLogSegments(instanceId, 0, -1);
        setSegments(segments.items);
        setCompleted(true);
        return !isFinal(processStatus) && processStatus !== ProcessStatus.SUSPENDED;
    }, [instanceId, processStatus]);

    const error = usePolling(fetchSegments, dataFetchInterval, loadingHandler, forceRefresh);

    const tree = useMemo(() => buildSegmentTree(segments), [segments]);

    useEffect(() => {
        if (!autoSelect) {
            return;
        }
        const node = pickDefaultSegment(tree, processStatus);
        if (node) {
            setSelectedId(node.segment.id);
        }
    }, [autoSelect, tree, processStatus]);

    const selectHandler = useCallback(
        (segmentId: number) => {
            setAutoSelect(false);
            setSelectedId(segmentId);
            navigate({ hash: `step=${segmentId}` }, { replace: true });
        },
        [navigate]
    );

    if (error && !completed) {
        return <RequestErrorActivity error={error} />;
    }

    const selected = selectedId !== undefined ? tree.byId.get(selectedId) : undefined;

    return (
        <>
            {error && (
                <div className="ProcessLogTreeError">
                    <RequestErrorMessage error={error} />
                    <button type="button" onClick={onRefresh}>
                        Retry
                    </button>
                </div>
            )}
            <div className="ProcessLogTreeActivity" ref={layoutRef} style={{ height }}>
                <div className="TreePane">
                    <LogSegmentTree
                        tree={tree}
                        processStatus={processStatus}
                        selectedId={selectedId}
                        onSelect={selectHandler}
                    />
                </div>
                <div className="ContentPane">
                    {selected && (
                        <StepHeader
                            instanceId={instanceId}
                            node={selected}
                            processStatus={processStatus}
                        />
                    )}
                    <div className="StepBody">
                        <LogView
                            instanceId={instanceId}
                            node={selected}
                            processStatus={processStatus}
                            dataFetchInterval={dataFetchInterval}
                            forceRefresh={forceRefresh}
                            pollingEnabled={
                                !isFinal(processStatus) &&
                                processStatus !== ProcessStatus.SUSPENDED
                            }
                            onSelect={selectHandler}
                        />
                        {!selected && (
                            <div className="Placeholder">
                                {segments.length === 0 ? 'No logs yet.' : 'Select a step.'}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </>
    );
};

const ProcessLogTreeActivity = (props: ExternalProps) => (
    <ProcessLogTreeActivityView key={props.instanceId} {...props} />
);

export default ProcessLogTreeActivity;
