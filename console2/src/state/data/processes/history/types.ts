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
import { ProcessHistoryEntry } from '../../../../api/process';
import { RequestState, CollectionById } from '../../common';
import { ConcordId } from '../../../../api/common';

export interface GetProcessHistory extends Action {
    instanceId: ConcordId;
}

export interface GetProcessHistroyResponse extends Action {
    items: ProcessHistoryEntry[];
}

export type GetProcessHistoryState = RequestState<GetProcessHistroyResponse>;
export type history = CollectionById<ProcessHistoryEntry>;

export interface State {
    getHistory: GetProcessHistoryState;
}
