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

import { combineReducers, Reducer } from 'redux';
import { delay } from 'redux-saga';
import { all, call, cancel, fork, put, race, take, takeLatest } from 'redux-saga/effects';

import { ConcordId } from '../../../../api/common';
import { get as apiGet, isFinal, ProcessEntry } from '../../../../api/process';
import { getLog as apiGetLog, LogChunk, LogRange } from '../../../../api/process/log';
import { handleErrors, makeErrorReducer, makeLoadingReducer } from '../../common';
import { LogProcessorOptions, process } from './processors';
import {
    GetProcessLogResponse,
    GetProcessLogState,
    LoadWholeProcessLog,
    LogSegment,
    LogSegmentType,
    StartProcessLogPolling,
    State
} from './types';

const NAMESPACE = 'processes/logs';

const actionTypes = {
    GET_PROCESS_LOG_REQUEST: `${NAMESPACE}/get/request`,
    GET_PROCESS_LOG_RESPONSE: `${NAMESPACE}/get/response`,

    START_PROCESS_LOG_POLLING: `${NAMESPACE}/poll/start`,
    STOP_PROCESS_LOG_POLLING: `${NAMESPACE}/poll/stop`,

    LOAD_WHOLE_PROCESS_LOG: `${NAMESPACE}/whole`,
    FORCE_PROCESS_LOG_REFRESH: `${NAMESPACE}/refresh`,

    RESET_PROCESS_LOG: `${NAMESPACE}/reset`
};

const defaultRange: LogRange = { low: undefined, high: 2048 };

export const actions = {
    startProcessLogPolling: (
        instanceId: ConcordId,
        opts: LogProcessorOptions,
        range: LogRange = defaultRange,
        reset: boolean = true
    ): StartProcessLogPolling => ({
        type: actionTypes.START_PROCESS_LOG_POLLING,
        instanceId,
        opts,
        range,
        reset
    }),

    stopProcessLogPolling: () => ({
        type: actionTypes.STOP_PROCESS_LOG_POLLING
    }),

    loadWholeLog: (instanceId: ConcordId, opts: LogProcessorOptions): LoadWholeProcessLog => ({
        type: actionTypes.LOAD_WHOLE_PROCESS_LOG,
        instanceId,
        opts
    }),

    logResponce: (
        chunk: LogChunk,
        overwrite: boolean,
        opts: LogProcessorOptions,
        process?: ProcessEntry
    ): GetProcessLogResponse => ({
        type: actionTypes.GET_PROCESS_LOG_RESPONSE,
        process,
        chunk,
        overwrite,
        opts
    }),

    forceRefresh: () => ({
        type: actionTypes.FORCE_PROCESS_LOG_REFRESH
    }),

    reset: () => ({
        type: actionTypes.RESET_PROCESS_LOG
    })
};

const processReducer: Reducer<ProcessEntry | null> = (
    state = null,
    { type, process }: GetProcessLogResponse
) => {
    switch (type) {
        case actionTypes.RESET_PROCESS_LOG:
            return null;
        case actionTypes.GET_PROCESS_LOG_RESPONSE:
            if (!process) {
                return state;
            }
            return process;
        default:
            return state;
    }
};

const dataReducer: Reducer<LogSegment[]> = (
    state: LogSegment[] = [],
    { type, error, chunk, overwrite, opts }: GetProcessLogResponse
) => {
    switch (type) {
        case actionTypes.RESET_PROCESS_LOG:
            return [];
        case actionTypes.GET_PROCESS_LOG_RESPONSE:
            if (error || !chunk) {
                return state;
            }

            const segment: LogSegment = { data: chunk.data, type: LogSegmentType.DATA };

            if (overwrite) {
                return [...process(segment, opts ? opts : {})];
            }

            if (chunk.data.length <= 0) {
                return state;
            }

            return [...state, ...process(segment, opts ? opts : {})];
        default:
            return state;
    }
};

const lengthReducer: Reducer<number> = (
    state: number = -1,
    { type, error, chunk }: GetProcessLogResponse
) => {
    switch (type) {
        case actionTypes.RESET_PROCESS_LOG:
            return -1;
        case actionTypes.GET_PROCESS_LOG_RESPONSE:
            if (error || !chunk) {
                return state;
            }

            const { range } = chunk;
            const l = range.length ? range.length : -1;
            return Math.max(state, l);
        default:
            return state;
    }
};

const completedReducer: Reducer<boolean> = (
    state: boolean = false,
    { type, error, chunk }: GetProcessLogResponse
) => {
    switch (type) {
        case actionTypes.RESET_PROCESS_LOG:
            return false;
        case actionTypes.GET_PROCESS_LOG_RESPONSE:
            if (error || !chunk) {
                return state;
            }

            const { range } = chunk;
            return state || range.low === 0;
        default:
            return state;
    }
};

const getLogReducers = combineReducers<GetProcessLogState>({
    running: makeLoadingReducer(
        [actionTypes.GET_PROCESS_LOG_REQUEST],
        [actionTypes.GET_PROCESS_LOG_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.GET_PROCESS_LOG_REQUEST],
        [actionTypes.GET_PROCESS_LOG_RESPONSE]
    ),
    response: (state = { data: '', range: {} }, {}) => state // eslint-disable-line no-empty-pattern
});

export const reducers = combineReducers<State>({
    process: processReducer,
    data: dataReducer,
    length: lengthReducer,
    completed: completedReducer,
    getLog: getLogReducers
});

function* doPoll(instanceId: ConcordId, opts: LogProcessorOptions, range: LogRange) {
    // copy the value
    const r = { ...range };

    try {
        while (true) {
            yield put({
                type: actionTypes.GET_PROCESS_LOG_REQUEST
            });

            const [proc, chunk] = yield all([
                call(apiGet, instanceId, []),
                call(apiGetLog, instanceId, r)
            ]);

            yield put(actions.logResponce(chunk, false, opts, proc));

            // adjust the range
            r.low = chunk.range.high;
            r.high = undefined;

            if (isFinal(proc.status)) {
                yield put(actions.stopProcessLogPolling());
                return;
            }

            yield race({
                delay: call(delay, 5000), // TODO constant
                forceRefresh: take(actionTypes.FORCE_PROCESS_LOG_REFRESH)
            });
        }
    } catch (e) {
        yield handleErrors(actionTypes.GET_PROCESS_LOG_RESPONSE, e);
    }
}

function* onStartPolling({ instanceId, opts, range, reset }: StartProcessLogPolling) {
    if (reset) {
        yield put(actions.reset());
    }

    const task = yield fork(doPoll, instanceId, opts, range);

    yield take(actionTypes.STOP_PROCESS_LOG_POLLING);
    yield cancel(task);
}

function* onLoadWholeLog({ instanceId, opts }: LoadWholeProcessLog) {
    try {
        // stop the polling, so it won't get in our way
        yield put(actions.stopProcessLogPolling());

        yield put({
            type: actionTypes.GET_PROCESS_LOG_REQUEST
        });

        // load the whole log
        const { data, range } = yield call(apiGetLog, instanceId, { low: 0 });

        // store the data
        yield put(actions.logResponce({ data, range }, true, opts));

        // adjust the range and continue the polling
        range.low = range.high;
        yield put(actions.startProcessLogPolling(instanceId, opts, range, false));
    } catch (e) {
        yield handleErrors(actionTypes.GET_PROCESS_LOG_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.START_PROCESS_LOG_POLLING, onStartPolling),
        takeLatest(actionTypes.LOAD_WHOLE_PROCESS_LOG, onLoadWholeLog)
    ]);
};
