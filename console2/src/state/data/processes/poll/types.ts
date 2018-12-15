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
import { ConcordId, RequestError } from '../../../../api/common';
import { ProcessEntry } from '../../../../api/process';
import { ProcessEventEntry } from '../../../../api/process/event';
import { FormListEntry } from '../../../../api/process/form';
import { RequestState } from '../../common';

export interface StartProcessPolling extends Action {
    instanceId: ConcordId;
    forceLoadAll?: boolean;
}

export interface ProcessEventChunk {
    replace: boolean;
    data?: Array<ProcessEventEntry<{}>>;
}

export interface ProcessPollResponse extends Action {
    error?: RequestError;
    process?: ProcessEntry;
    forms?: FormListEntry[];
    events?: ProcessEventChunk;
    tooMuchData?: boolean;
}

export type ProcessPollState = RequestState<ProcessPollResponse>;

export interface ProcessEvents {
    [id: string]: ProcessEventEntry<{}>;
}

export interface State {
    currentRequest: ProcessPollState;
    forms: FormListEntry[];
    eventById: ProcessEvents;
}
