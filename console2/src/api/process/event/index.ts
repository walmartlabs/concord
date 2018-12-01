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

// TODO find which properties are always defined

export interface ProcessElementEvent {
    processDefinitionId: string;
    elementId: string;
    line: number;
    column: number;
    description?: string;
    phase?: 'pre' | 'post';
    out?: {};
    correlationId?: string;
}

export type ProcessEventData = ProcessElementEvent | AnsibleEvent | {};

export interface ProcessEventEntry<T extends ProcessEventData> {
    id: ConcordId;
    eventType: ProcessEventType;
    eventDate: string;
    duration?: number;
    data: T;
}

export const listEvents = (
    instanceId: ConcordId,
    type?: string,
    after?: string,
    limit?: number
): Promise<ProcessEventEntry<{}>> =>
    fetchJson(
        `/api/v1/process/${instanceId}/event?${queryParams({
            type,
            after,
            limit
        })}`
    );
