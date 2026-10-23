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
import { TreeLoader } from './TreeLoader';
import { usePolling } from '../../../api/usePolling';
import RequestErrorActivity from '../RequestErrorActivity';
import RequestErrorMessage from '../../molecules/RequestErrorMessage';
import LogSegmentTree from './LogSegmentTree';
import LogView from './LogView';
import StepHeader from './StepHeader';
import { buildSegmentTree } from './segmentTree';

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
    const [revision, setRevision] = useState(0);
    const loader = useMemo(
        () => new TreeLoader(instanceId, () => setRevision((n) => n + 1)),
        [instanceId]
    );
    const selectedId = loader.selectedId;
    const segments = loader.segments;
    const completed = (loader.branch()?.chunks.length ?? 0) > 0;
    useEffect(() => {
        loader.start();
        const linked = getLinkedStepId(location.hash);
        if (linked !== undefined) loader.select(linked);
        return loader.dispose;
        // The process-scoped view is remounted when instanceId changes.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [loader]);
    useEffect(() => {
        const linked = getLinkedStepId(location.hash);
        if (linked !== undefined && linked !== loader.selectedId) loader.select(linked);
    }, [loader, location.hash]);

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
        loader.refresh();
        return !isFinal(processStatus) && processStatus !== ProcessStatus.SUSPENDED;
    }, [loader, processStatus]);
    usePolling(fetchSegments, dataFetchInterval, loadingHandler, forceRefresh);
    const error = loader.error ?? loader.branch()?.error;
    const tree = useMemo(
        () =>
            buildSegmentTree(
                segments.map((e) => e.segment),
                loader.entries
            ),
        [loader, revision]
    );

    useEffect(() => {
        if (loader.selectedId === undefined && tree.roots.length) {
            loader.selectDefault(!isFinal(processStatus));
        }
    }, [loader, tree, processStatus]);
    useEffect(() => {
        if (selectedId !== undefined) navigate({ hash: `step=${selectedId}` }, { replace: true });
    }, [selectedId, navigate]);
    const selectHandler = loader.select;

    if (error && !completed) {
        return <RequestErrorActivity error={error} />;
    }

    // Filtering the tree must not filter or unmount the selected log viewer.
    const selected =
        selectedId !== undefined
            ? buildSegmentTree(
                  [...loader.entries.values()]
                      .map((entry) => entry.segment)
                      .sort((a, b) => a.id - b.id),
                  loader.entries
              ).byId.get(selectedId)
            : undefined;

    return (
        <>
            {error && (
                <div className="ProcessLogTreeError">
                    <RequestErrorMessage error={error} />
                    <button
                        type="button"
                        onClick={() => {
                            loader.refresh();
                            onRefresh();
                        }}
                    >
                        Retry
                    </button>
                </div>
            )}
            <div className="ProcessLogTreeActivity" ref={layoutRef} style={{ height }}>
                <div className="TreePane">
                    <LogSegmentTree
                        tree={tree}
                        remote={loader}
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
                                !isFinal(processStatus) && processStatus !== ProcessStatus.SUSPENDED
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

const ProcessLogTreeActivity = React.memo((props: ExternalProps) => (
    <ProcessLogTreeActivityView key={props.instanceId} {...props} />
));

export default ProcessLogTreeActivity;
