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

import { useEffect, useRef, useState } from 'react';

import { ConcordId } from '../../../api/common';
import { getSegmentLog } from '../../../api/process/log';

const POLL_INTERVAL = 2000;
const CONCURRENCY = 4;

export interface SegmentLog {
    text: string;
    // the beginning of the log is not loaded
    truncated: boolean;
    totalBytes?: number;
    loaded: boolean;
    error?: string;
}

export interface SegmentLogRequest {
    id: number;
    // keep loading new data
    live: boolean;
    // how much of the tail to load at first
    tailBytes: number;
    // load the whole log
    full: boolean;
}

interface Entry extends SegmentLog {
    // the offset to load new data from
    next?: number;
    full: boolean;
    busy: boolean;
}

const EMPTY: SegmentLog = { text: '', truncated: false, loaded: false };

/**
 * Loads the logs of several segments: the tails first, then the new data of the running ones.
 * Already loaded logs are kept when the list of segments changes (e.g. new nested steps appear).
 */
export const useSegmentLogs = (
    instanceId: ConcordId,
    requests: SegmentLogRequest[]
): ((segmentId: number) => SegmentLog) => {
    const store = useRef(new Map<number, Entry>());
    const requestsRef = useRef(requests);
    requestsRef.current = requests;
    const [, setVersion] = useState(0);

    useEffect(() => {
        let cancelled = false;
        const queue: SegmentLogRequest[] = [];
        let active = 0;

        const load = async (r: SegmentLogRequest) => {
            const e = store.current.get(r.id)!;
            const initial = e.next === undefined || (r.full && !e.full);
            try {
                const chunk = await getSegmentLog(
                    instanceId,
                    r.id,
                    initial ? (r.full ? { low: 0 } : { high: r.tailBytes }) : { low: e.next }
                );
                if (cancelled) {
                    return;
                }

                let data = chunk.data;
                const truncated = initial && (chunk.range.low ?? 0) > 0;
                if (truncated) {
                    // the tail starts in the middle of a line
                    data = data.substring(data.indexOf('\n') + 1);
                }

                store.current.set(r.id, {
                    ...e,
                    text: initial ? data : e.text + data,
                    truncated: initial ? truncated : e.truncated,
                    totalBytes: chunk.range.length,
                    next: chunk.range.high ?? e.next,
                    full: e.full || r.full,
                    loaded: true,
                    error: undefined,
                    busy: false,
                });
            } catch (err) {
                store.current.set(r.id, {
                    ...e,
                    loaded: true,
                    error: err instanceof Error ? err.message : String(err),
                    busy: false,
                });
            }
            setVersion((v) => v + 1);
        };

        const pump = () => {
            while (!cancelled && active < CONCURRENCY && queue.length > 0) {
                const r = queue.shift()!;
                active++;
                load(r).finally(() => {
                    active--;
                    pump();
                });
            }
        };

        const schedule = (r: SegmentLogRequest) => {
            const e = store.current.get(r.id) ?? { ...EMPTY, full: false, busy: false };
            if (e.busy) {
                return;
            }
            store.current.set(r.id, { ...e, busy: true });
            queue.push(r);
        };

        // the logs that are not loaded yet, in the order of the requests
        requests.forEach((r) => {
            const e = store.current.get(r.id);
            if (!e?.loaded || (r.full && !e.full)) {
                schedule(r);
            }
        });
        pump();

        // new data of the running segments
        const timer = window.setInterval(() => {
            requestsRef.current
                .filter((r) => r.live && store.current.get(r.id)?.loaded)
                .forEach(schedule);
            pump();
        }, POLL_INTERVAL);

        return () => {
            cancelled = true;
            window.clearInterval(timer);
            // allow reloading of the interrupted requests
            store.current.forEach((e, id) => store.current.set(id, { ...e, busy: false }));
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [instanceId, requests.map((r) => `${r.id}:${r.full}`).join(',')]);

    return (segmentId) => store.current.get(segmentId) ?? EMPTY;
};
