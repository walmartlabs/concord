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

import { ConcordId, fetchJson, queryParams } from '../../common';
import { AnsibleEvent } from '../ansible';

export enum ProcessEventType {
    ELEMENT = 'ELEMENT',
    ANSIBLE = 'ANSIBLE'
}

export interface VariableMapping {
    source?: string;
    sourceExpression?: string;
    sourceValue: any;
    target: string;
    resolved: any;
}

// TODO find which properties are always defined
export interface ProcessElementEvent {
    processDefinitionId: string;
    threadId?: number;
    fileName?: string;
    elementId: string;
    line: number;
    column: number;
    description?: string;
    phase?: 'pre' | 'post';
    in?: VariableMapping[] | {};
    out?: VariableMapping[] | {};
    correlationId?: string;
    // the log segment of the step run (missing in events of older processes)
    logSegmentId?: number;
    duration?: number;
    error?: string;
}

export type ProcessEventData = ProcessElementEvent | AnsibleEvent | {};

export interface ProcessEventFilter {
    instanceId: ConcordId;
    type?: string;
    fromId?: number;
    eventCorrelationId?: string;
    eventPhase?: 'PRE' | 'POST';
    includeAll?: boolean;
    limit?: number;
}

export interface ProcessEventEntry<T extends ProcessEventData> {
    id: ConcordId;
    seqId: number;
    eventType: ProcessEventType;
    eventDate: string;
    data: T;
}

/**
 * ELEMENT events of the step run that produced the log segment. Unlike the correlation ID,
 * distinguishes loop iterations and retry attempts of the same step.
 */
export const listSegmentEvents = <T extends ProcessEventData>(
    instanceId: ConcordId,
    segmentId: number,
    includeAll = false
): Promise<ProcessEventEntry<T>[]> =>
    fetchJson(
        `/api/v1/process/${instanceId}/event/segment/${segmentId}?${queryParams({ includeAll })}`
    );

export interface StepRef {
    segmentId?: number;
    correlationId: string;
    // a loop iteration or a retry attempt: the correlation ID is shared with other runs
    repeated: boolean;
}

/**
 * ELEMENT events of a step run. Processes started before the events were linked to log segments
 * have none: for them the events are looked up by the correlation ID, which is unambiguous only
 * for steps that ran once.
 */
export const listStepEvents = async <T extends ProcessEventData>(
    instanceId: ConcordId,
    step: StepRef,
    includeAll = false
): Promise<ProcessEventEntry<T>[]> => {
    if (step.segmentId !== undefined) {
        const events = await listSegmentEvents<T>(instanceId, step.segmentId, includeAll);
        if (events.length > 0 || step.repeated) {
            return events;
        }
    }

    return listEvents<T>({
        instanceId,
        type: 'ELEMENT',
        eventCorrelationId: step.correlationId,
        includeAll,
        limit: 2,
    });
};

export const listEvents = <T extends ProcessEventData>(
    filter: ProcessEventFilter
): Promise<ProcessEventEntry<T>[]> =>
    fetchJson(`/api/v1/process/${filter.instanceId}/event?${queryParams({ ...filter })}`);
