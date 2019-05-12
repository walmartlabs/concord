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
import { ProcessStatus } from '../../../../api/process';
import { LogChunk, LogRange } from '../../../../api/process/log';
import { RequestState } from '../../common';
import { LogProcessorOptions } from './processors';

export interface StartProcessLogPolling extends Action {
    instanceId: ConcordId;
    opts: LogProcessorOptions;
    range: LogRange;
    reset: boolean;
}

export interface LoadWholeProcessLog extends Action {
    instanceId: ConcordId;
    opts: LogProcessorOptions;
}

export interface GetProcessLogResponse extends Action {
    status?: ProcessStatus;
    error?: RequestError;
    chunk?: LogChunk;
    overwrite?: boolean;
    opts?: LogProcessorOptions;
}

export type GetProcessLogState = RequestState<LogChunk>;

export enum LogSegmentType {
    DATA,
    TAG
}

export interface TagData {
    phase: 'pre' | 'post';
    taskName: string;
}

export interface LogSegment {
    data: string | TagData;
    type: LogSegmentType;
}

export interface State {
    status: ProcessStatus | null;
    data: LogSegment[];
    length: number;
    completed: boolean;

    getLog: GetProcessLogState;
}
