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
import { SemanticCOLORS } from 'semantic-ui-react';

import { ConcordId, fetchJson, queryParams } from '../../common';

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
    in?: {};
    correlationId?: string;
}

export enum AnsibleStatus {
    RUNNING = 'RUNNING',
    CHANGED = 'CHANGED',
    FAILED = 'FAILED',
    OK = 'OK',
    SKIPPED = 'SKIPPED',
    UNREACHABLE = 'UNREACHABLE'
}

export const getStatusSemanticColor = (status: AnsibleStatus): SemanticCOLORS => {
    switch (status) {
        case AnsibleStatus.OK:
            return 'green';
        case AnsibleStatus.CHANGED:
            return 'blue';
        case AnsibleStatus.FAILED:
            return 'red';
        case AnsibleStatus.UNREACHABLE:
            return 'grey';
        case AnsibleStatus.SKIPPED:
            return 'yellow';
        default:
            return 'grey';
    }
};

export const getStatusColor = (status: AnsibleStatus) => {
    switch (status) {
        case AnsibleStatus.OK:
            return '#5DB571';
        case AnsibleStatus.CHANGED:
            return '#00A4D3';
        case AnsibleStatus.FAILED:
            return '#EC6357';
        case AnsibleStatus.UNREACHABLE:
            return '#BDB9B9';
        case AnsibleStatus.SKIPPED:
            return '#F6BC32';
        default:
            return '#3F3F3D';
    }
};

export interface AnsibleEvent {
    host: string;
    hostGroup: string;
    playbook: string;
    status: AnsibleStatus;
    task: string;
    result?: object;
    ignore_errors?: boolean;
    phase: 'pre' | 'post';
    correlationId: string;
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
    after?: string,
    limit?: number
): Promise<ProcessEventEntry<{}>> =>
    fetchJson(
        `/api/v1/process/${instanceId}/event?${queryParams({
            after,
            limit
        })}`
    );
