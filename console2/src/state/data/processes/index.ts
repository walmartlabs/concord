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

import { Action, combineReducers, Reducer } from 'redux';
import { all, call, fork, put, takeLatest, throttle } from 'redux-saga/effects';

import { ConcordId, ConcordKey } from '../../../api/common';
import { list as apiProcessList, ProcessListQuery } from '../../../api/process';
import { start as apiStart, killBulk as apiKillBulk } from '../../../api/process';
import { restoreProcess as apiRestore } from '../../../api/process/checkpoint';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import { reducers as eventsReducers, sagas as eventsSagas } from './events';

import {
    CancelBulkProcessRequest,
    CancelBullkProcessState,
    ListProcessesRequest,
    PaginatedProcessDataResponse,
    PaginatedProcesses,
    RestoreProcessRequest,
    RestoreProcessState,
    StartProcessRequest,
    StartProcessState,
    State
} from './types';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'processes';

const actionTypes = {
    LIST_PROJECT_PROCESSES_REQUEST: `${NAMESPACE}/project/list/request`,
    PROCESS_DATA_RESPONSE: `${NAMESPACE}/data/response`,
    PROCESSES_DATA_RESPONSE: `${NAMESPACE}/processes/data/response`,

    START_PROCESS_REQUEST: `${NAMESPACE}/start/request`,
    START_PROCESS_RESPONSE: `${NAMESPACE}/start/response`,

    CANCEL_BULK_PROCESS_REQUEST: `${NAMESPACE}/cancel/bulk/process/request`,
    CANCEL_BULK_PROCESS_RESPONSE: `${NAMESPACE}/cancel/bulk/process/response`,

    RESTORE_PROCESS_REQUEST: `${NAMESPACE}/restore/request`,
    RESTORE_PROCESS_RESPONSE: `${NAMESPACE}/restore/response`,

    RESET_PROCESS: `${NAMESPACE}/reset`,
    RESET_BULK_PROCESS: `${NAMESPACE}/reset/bulk`
};

export const actions = {
    listProcesses: (query: ProcessListQuery): ListProcessesRequest => ({
        type: actionTypes.LIST_PROJECT_PROCESSES_REQUEST,
        query
    }),

    startProcess: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        repoName: ConcordKey,
        entryPoint?: string,
        profiles?: string[],
        args?: object
    ): StartProcessRequest => ({
        type: actionTypes.START_PROCESS_REQUEST,
        orgName,
        projectName,
        repoName,
        entryPoint,
        profiles,
        args
    }),

    restoreProcess: (instanceId: ConcordKey, checkpointId: ConcordKey): RestoreProcessRequest => ({
        type: actionTypes.RESTORE_PROCESS_REQUEST,
        instanceId,
        checkpointId
    }),

    cancelBulk: (instanceIds: ConcordId[]): CancelBulkProcessRequest => ({
        type: actionTypes.CANCEL_BULK_PROCESS_REQUEST,
        instanceIds
    }),
    resetBulk: () => ({
        type: actionTypes.RESET_BULK_PROCESS
    }),

    reset: () => ({
        type: actionTypes.RESET_PROCESS
    })
};

const paginatedProcessesById: Reducer<PaginatedProcesses> = (
    state = { processes: {} },
    { type, error, items, next, prev }: PaginatedProcessDataResponse
) => {
    switch (type) {
        case actionTypes.PROCESSES_DATA_RESPONSE:
            if (error || !items) {
                return state;
            }

            const result = {};

            items.forEach((o) => {
                result[o.instanceId] = o;
            });

            return { processes: result, next, prev };
        default:
            return state;
    }
};

const loading = makeLoadingReducer(
    [actionTypes.LIST_PROJECT_PROCESSES_REQUEST],
    [actionTypes.PROCESS_DATA_RESPONSE, actionTypes, actionTypes.PROCESSES_DATA_RESPONSE]
);

const errorMsg = makeErrorReducer(
    [actionTypes.LIST_PROJECT_PROCESSES_REQUEST],
    [actionTypes.PROCESS_DATA_RESPONSE, actionTypes.PROCESSES_DATA_RESPONSE]
);

const startProcessReducers = combineReducers<StartProcessState>({
    running: makeLoadingReducer(
        [actionTypes.START_PROCESS_REQUEST],
        [actionTypes.START_PROCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_PROCESS, actionTypes.START_PROCESS_REQUEST],
        [actionTypes.START_PROCESS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.START_PROCESS_RESPONSE, actionTypes.RESET_PROCESS)
});

const restoreProcessReducers = combineReducers<RestoreProcessState>({
    running: makeLoadingReducer(
        [actionTypes.RESTORE_PROCESS_REQUEST],
        [actionTypes.RESTORE_PROCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_PROCESS, actionTypes.RESTORE_PROCESS_REQUEST],
        [actionTypes.RESTORE_PROCESS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.RESTORE_PROCESS_RESPONSE, actionTypes.RESET_PROCESS)
});

const cancelBulkProcessReducers = combineReducers<CancelBullkProcessState>({
    running: makeLoadingReducer(
        [actionTypes.CANCEL_BULK_PROCESS_REQUEST],
        [actionTypes.RESET_BULK_PROCESS, actionTypes.CANCEL_BULK_PROCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_BULK_PROCESS, actionTypes.CANCEL_BULK_PROCESS_REQUEST],
        [actionTypes.CANCEL_BULK_PROCESS_RESPONSE]
    ),
    response: (state = false, { type, error }: Action & { error?: {} }) => {
        switch (type) {
            case actionTypes.RESET_BULK_PROCESS:
                return false;
            case actionTypes.CANCEL_BULK_PROCESS_RESPONSE:
                return !error;
            default:
                return state;
        }
    }
});

export const reducers = combineReducers<State>({
    paginatedProcessesById,

    loading,
    error: errorMsg,

    startProcess: startProcessReducers,
    restoreProcess: restoreProcessReducers,
    cancelBulkProcess: cancelBulkProcessReducers,

    events: eventsReducers
});

function* onProcessList({ query }: ListProcessesRequest) {
    try {
        const response = yield call(apiProcessList, query);
        yield put({
            type: actionTypes.PROCESSES_DATA_RESPONSE,
            items: response.items,
            next: response.next,
            prev: response.prev
        });
    } catch (e) {
        yield handleErrors(actionTypes.PROCESSES_DATA_RESPONSE, e);
    }
}

function* onStartProcess({
    orgName,
    projectName,
    repoName,
    entryPoint,
    profiles,
    args
}: StartProcessRequest) {
    try {
        const response = yield call(
            apiStart,
            orgName,
            projectName,
            repoName,
            entryPoint,
            profiles,
            args
        );
        yield put({
            type: actionTypes.START_PROCESS_RESPONSE,
            ...response
        });
    } catch (e) {
        yield handleErrors(actionTypes.START_PROCESS_RESPONSE, e);
    }
}

function* onCancelBulkProcess({ instanceIds }: CancelBulkProcessRequest) {
    try {
        yield call(apiKillBulk, instanceIds);
        yield put({
            type: actionTypes.CANCEL_BULK_PROCESS_RESPONSE
        });
    } catch (e) {
        yield handleErrors(actionTypes.CANCEL_BULK_PROCESS_RESPONSE, e);
    }
}

function* onRestoreProcess({ instanceId, checkpointId }: RestoreProcessRequest) {
    try {
        const response = yield call(apiRestore, instanceId, checkpointId);
        yield put({
            type: actionTypes.RESTORE_PROCESS_RESPONSE,
            ...response
        });
    } catch (e) {
        yield handleErrors(actionTypes.RESTORE_PROCESS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        throttle(1000, actionTypes.LIST_PROJECT_PROCESSES_REQUEST, onProcessList),
        takeLatest(actionTypes.START_PROCESS_REQUEST, onStartProcess),
        takeLatest(actionTypes.CANCEL_BULK_PROCESS_REQUEST, onCancelBulkProcess),
        takeLatest(actionTypes.RESTORE_PROCESS_REQUEST, onRestoreProcess),
        fork(eventsSagas)
    ]);
};
