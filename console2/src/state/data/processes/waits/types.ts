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
import { ConcordId } from '../../../../api/common';
import { ProcessWaitHistoryEntry } from '../../../../api/process';
import { CollectionById, RequestState } from '../../common';

export interface GetProcessWait extends Action {
    instanceId: ConcordId;
}

export interface GetProcessWaitResponse extends Action {
    items: ProcessWaitHistoryEntry[];
}

export type GetProcessWaitState = RequestState<GetProcessWaitResponse>;
export type history = CollectionById<ProcessWaitHistoryEntry>;

export interface State {
    getWait: GetProcessWaitState;
}
