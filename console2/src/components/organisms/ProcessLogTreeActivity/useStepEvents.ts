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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { toRequestError } from '../../../api/common';
import type { ConcordId, RequestErrorData } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { listStepEvents, ProcessElementEvent, ProcessEventEntry } from '../../../api/process/event';
import { LogSegmentEntry } from '../../../api/process/log';

const RETRY_INTERVAL = 2_000;
const MAX_ATTEMPTS = 10;

interface Options {
    includeAll: boolean;
    enabled: boolean;
    waitForPost: boolean;
    processStatus?: ProcessStatus;
}

interface State {
    identity: string;
    events?: ProcessEventEntry<ProcessElementEvent>[];
    error?: RequestErrorData;
    exhausted: boolean;
}

export interface StepEventsResult {
    events?: ProcessEventEntry<ProcessElementEvent>[];
    error?: RequestErrorData;
    exhausted: boolean;
    refresh: () => void;
}

/**
 * Loads events that can arrive after their log segment, retrying incomplete responses without
 * tying a request generation to the identity of a replacement segment object.
 */
export const useStepEvents = (
    instanceId: ConcordId,
    run: LogSegmentEntry | undefined,
    { includeAll, enabled, waitForPost, processStatus }: Options
): StepEventsResult => {
    const identity = useMemo(
        () =>
            JSON.stringify([
                instanceId,
                run?.id,
                run?.correlationId,
                run?.attempt,
                run?.loopIndex,
                includeAll,
            ]),
        [instanceId, run?.id, run?.correlationId, run?.attempt, run?.loopIndex, includeAll]
    );
    const [state, setState] = useState<State>({ identity, exhausted: false });
    const [refreshCounter, setRefreshCounter] = useState(0);
    const generation = useRef(0);

    const refresh = useCallback(() => setRefreshCounter((value) => value + 1), []);

    useEffect(() => {
        const currentGeneration = ++generation.current;
        let timer: number | undefined;
        let cancelled = false;
        let attempt = 0;

        setState((previous) =>
            previous.identity === identity
                ? { ...previous, error: undefined, exhausted: false }
                : { identity, exhausted: false }
        );

        if (!enabled || !run?.correlationId) {
            return () => {
                cancelled = true;
            };
        }

        const load = async () => {
            attempt += 1;
            try {
                const events = await listStepEvents<ProcessElementEvent>(
                    instanceId,
                    {
                        segmentId: run.id,
                        correlationId: run.correlationId!,
                        repeated: run.attempt !== undefined || run.loopIndex !== undefined,
                    },
                    includeAll
                );
                if (cancelled || generation.current !== currentGeneration) {
                    return;
                }

                const complete =
                    events.length > 0 &&
                    (!waitForPost || events.some((event) => event.data.phase === 'post'));
                const exhausted = !complete && attempt >= MAX_ATTEMPTS;
                setState({ identity, events, exhausted });

                if (!complete && !exhausted) {
                    timer = window.setTimeout(load, RETRY_INTERVAL);
                }
            } catch (error) {
                if (!cancelled && generation.current === currentGeneration) {
                    setState((previous) => ({
                        identity,
                        events: previous.identity === identity ? previous.events : undefined,
                        error: toRequestError(error),
                        exhausted: false,
                    }));
                }
            }
        };

        void load();

        return () => {
            cancelled = true;
            window.clearTimeout(timer);
        };
    }, [
        enabled,
        identity,
        includeAll,
        instanceId,
        processStatus,
        refreshCounter,
        run?.attempt,
        run?.correlationId,
        run?.id,
        run?.loopIndex,
        run?.status,
        waitForPost,
    ]);

    if (state.identity !== identity) {
        return { events: undefined, error: undefined, exhausted: false, refresh };
    }
    return { events: state.events, error: state.error, exhausted: state.exhausted, refresh };
};
