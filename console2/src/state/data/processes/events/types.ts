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

import { Action } from 'redux';

import { AnsibleEvent, AnsibleStatus } from '../../../../api/process/ansible';
import { ProcessEventEntry } from '../../../../api/process/event';
import { ConcordId, RequestError } from '../../../../api/common';

export interface GetAnsibleEventsRequest extends Action {
    instanceId: ConcordId;
    host?: string;
    hostGroup?: string;
    status?: AnsibleStatus;
}

export interface GetAnsibleEventsResponse extends Action {
    error: RequestError;
    events?: Array<ProcessEventEntry<{}>>;
}

export interface AnsibleEvents {
    [id: string]: ProcessEventEntry<AnsibleEvent>;
}

export interface State {
    ansibleEvents: AnsibleEvents;
    loading: boolean;
    error: RequestError;
}
