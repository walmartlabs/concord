/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { StartProcessResponse } from '../../../api/org/process';
import { ProcessEntry } from '../../../api/process';
import { RequestState } from '../common';
import { State as LogState } from './logs/types';
import { State as PollState } from './poll/types';

export interface ListProjectProcessesRequest extends Action {
    orgName?: ConcordKey;
    projectName?: ConcordKey;
}

export interface ProcessDataResponse extends Action {
    // TODO replace with RequestState<ProcessDataResponse>
    error: RequestError;
    items?: ProcessEntry[];
}

export interface StartProcessRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

export interface CancelProcessRequest extends Action {
    instanceId: ConcordId;
}

export interface Processes {
    [id: string]: ProcessEntry;
}

export type StartProcessState = RequestState<StartProcessResponse>;
export type CancelProcessState = RequestState<boolean>;

export interface State {
    processById: Processes;
    loading: boolean;
    error: RequestError;

    startProcess: StartProcessState;
    cancelProcess: CancelProcessState;

    log: LogState;
    poll: PollState;
}
