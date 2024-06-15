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

import { isFinal, ProcessStatus } from '../../../api/process';
import {
    listEvents as apiListEvents,
    ProcessElementEvent,
    ProcessEventEntry
} from '../../../api/process/event';
import { AnsibleEvent } from '../../../api/process/ansible';
import { ProcessElementList } from '../../molecules';
import { useCallback, useRef } from 'react';
import { useState } from 'react';
import { usePolling } from '../../../api/usePolling';
import { ConcordId } from '../../../api/common';
import RequestErrorActivity from '../RequestErrorActivity';

interface ExternalProps {
    instanceId: ConcordId;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
    processStatus?: ProcessStatus;
    definitionLinkBase?: string;
    dataFetchInterval: number;
}


const ProcessEventsActivity = (props: ExternalProps) => {
    const { instanceId, processStatus, loadingHandler, forceRefresh, definitionLinkBase, dataFetchInterval } = props;

    const lastEventId = useRef<number>();

    const [events, setEvents] = useState<ProcessEventEntry<ProcessElementEvent>[]>();

    const fetchData = useCallback(async () => {
        const events = await apiListEvents<ProcessElementEvent>({
            instanceId: instanceId,
            type: 'ELEMENT',
            fromId: lastEventId.current,
            limit: 100
        });

        if (events.length > 0) {
            lastEventId.current = Math.max.apply(
                Math,
                events.map((e) => e.seqId)
            );
        }

        setEvents((prevEvents) => reduceEvents(prevEvents ? prevEvents : [], events));

        return !(events.length === 0 && isFinal(processStatus));
    }, [instanceId, processStatus]);

    const error = usePolling(fetchData, dataFetchInterval, loadingHandler, forceRefresh);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <ProcessElementList
            instanceId={instanceId}
            events={events}
            processStatus={processStatus}
            definitionLinkBase={definitionLinkBase}
        />
    );
};

const reduceEvents = (
    prevEvents: ProcessEventEntry<ProcessElementEvent>[],
    events: ProcessEventEntry<ProcessElementEvent>[]
): ProcessEventEntry<ProcessElementEvent>[] => {
    function hasEvent(id: ConcordId, events: ProcessEventEntry<ProcessElementEvent>[]): boolean {
        return events.find((value) => value.id === id) !== undefined;
    }

    const newEvents = events.filter((value) => !hasEvent(value.id, prevEvents));
    if (newEvents.length === 0) {
        return prevEvents;
    }

    return combinePrePostEvents(prevEvents.concat(sortEvents(newEvents))) as ProcessEventEntry<
        ProcessElementEvent
    >[];
};

const sortEvents = (
    events: ProcessEventEntry<ProcessElementEvent>[]
): Array<ProcessEventEntry<ProcessElementEvent>> => {
    return events.sort((a, b) =>
        a.eventDate > b.eventDate ? 1 : a.eventDate < b.eventDate ? -1 : 0
    );
};

export const combinePrePostEvents = (
    events: Array<ProcessEventEntry<AnsibleEvent | ProcessElementEvent>>
): Array<ProcessEventEntry<AnsibleEvent | ProcessElementEvent>> => {
    function findEvent(
        phase: string,
        correlationId: string
    ): ProcessEventEntry<AnsibleEvent | ProcessElementEvent> | undefined {
        return events.find(
            (value) => value.data.phase === phase && value.data.correlationId === correlationId
        );
    }

    const processed = {};
    const result = new Array<ProcessEventEntry<AnsibleEvent | ProcessElementEvent>>();
    events.forEach((event) => {
        const data = event.data;
        if (!data.correlationId) {
            result.push(event);
            return;
        }

        if (processed[data.correlationId]) {
            return;
        }

        processed[data.correlationId] = true;

        if (data.phase === 'pre' && data.correlationId) {
            const postEvent = findEvent('post', data.correlationId);
            if (postEvent) {
                const clone = { ...event };
                clone.data = postEvent.data;
                result.push(clone);
            } else {
                result.push(event);
            }
        } else if (data.phase === 'post' && data.correlationId) {
            const preEvent = findEvent('pre', data.correlationId);
            if (preEvent) {
                const clone = { ...preEvent };
                clone.data = event.data;
                result.push(clone);
            } else {
                result.push(event);
            }
        } else {
            result.push(event);
        }
    });

    return result;
};

export default ProcessEventsActivity;
