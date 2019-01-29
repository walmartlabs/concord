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
import {
    list as apiProcessList,
    PaginationFilters,
    ProcessFilters
} from '../../../api/org/process';
import {
    get as apiGet,
    kill as apiKill,
    start as apiStart,
    killBulk as apiKillBulk,
    ProcessDataInclude
} from '../../../api/process';
import { restoreProcess as apiRestore } from '../../../api/process/checkpoint';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import { reducers as logReducers, sagas as logSagas } from './logs';
import { actions as pollActions, reducers as pollReducers, sagas as pollSagas } from './poll';
import { reducers as historyReducers, sagas as historySagas } from './history';
import { reducers as attachmentReducers, sagas as attachmentSagas } from './attachments';
import { reducers as childrenReducers, sagas as childrenSagas } from './children';
import { reducers as eventsReducers, sagas as eventsSagas } from './events';
import { reducers as ansibleReducers, sagas as ansibleSagas } from './ansible';

import {
    CancelBulkProcessRequest,
    CancelBullkProcessState,
    CancelProcessRequest,
    CancelProcessState,
    GetProcessRequest,
    ListProcessesRequest,
    PaginatedProcessDataResponse,
    PaginatedProcesses,
    ProcessDataResponse,
    Processes,
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
    GET_PROCESS_REQUEST: `${NAMESPACE}/get/request`,
    LIST_PROJECT_PROCESSES_REQUEST: `${NAMESPACE}/project/list/request`,
    PROCESS_DATA_RESPONSE: `${NAMESPACE}/data/response`,
    PROCESSES_DATA_RESPONSE: `${NAMESPACE}/processes/data/response`,

    START_PROCESS_REQUEST: `${NAMESPACE}/start/request`,
    START_PROCESS_RESPONSE: `${NAMESPACE}/start/response`,

    CANCEL_PROCESS_REQUEST: `${NAMESPACE}/cancel/request`,
    CANCEL_PROCESS_RESPONSE: `${NAMESPACE}/cancel/response`,

    CANCEL_BULK_PROCESS_REQUEST: `${NAMESPACE}/cancel/bulk/process/request`,
    CANCEL_BULK_PROCESS_RESPONSE: `${NAMESPACE}/cancel/bulk/process/response`,

    RESTORE_PROCESS_REQUEST: `${NAMESPACE}/restore/request`,
    RESTORE_PROCESS_RESPONSE: `${NAMESPACE}/restore/response`,

    RESET_PROCESS: `${NAMESPACE}/reset`,
    RESET_BULK_PROCESS: `${NAMESPACE}/reset/bulk`
};

export const actions = {
    getProcess: (instanceId: ConcordId, includes: ProcessDataInclude): GetProcessRequest => ({
        type: actionTypes.GET_PROCESS_REQUEST,
        instanceId,
        includes
    }),

    listProcesses: (
        orgName?: ConcordKey,
        projectName?: ConcordKey,
        filters?: ProcessFilters,
        pagination?: PaginationFilters
    ): ListProcessesRequest => ({
        type: actionTypes.LIST_PROJECT_PROCESSES_REQUEST,
        orgName,
        projectName,
        filters,
        pagination
    }),

    startProcess: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        repoName: ConcordKey,
        entryPoint: string,
        profile: string
    ): StartProcessRequest => ({
        type: actionTypes.START_PROCESS_REQUEST,
        orgName,
        projectName,
        repoName,
        entryPoint,
        profile
    }),

    restoreProcess: (instanceId: ConcordKey, checkpointId: ConcordKey): RestoreProcessRequest => ({
        type: actionTypes.RESTORE_PROCESS_REQUEST,
        instanceId,
        checkpointId
    }),

    cancel: (instanceId: ConcordId): CancelProcessRequest => ({
        type: actionTypes.CANCEL_PROCESS_REQUEST,
        instanceId
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

const processesById: Reducer<Processes> = (
    state = {},
    { type, error, items }: ProcessDataResponse
) => {
    switch (type) {
        case actionTypes.PROCESS_DATA_RESPONSE:
            if (error || !items) {
                return {};
            }

            const result = {};
            items.forEach((o) => {
                result[o.instanceId] = o;
            });
            return result;
        default:
            return state;
    }
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
    [actionTypes.GET_PROCESS_REQUEST, actionTypes.LIST_PROJECT_PROCESSES_REQUEST],
    [actionTypes.PROCESS_DATA_RESPONSE, actionTypes, actionTypes.PROCESSES_DATA_RESPONSE]
);

const errorMsg = makeErrorReducer(
    [actionTypes.GET_PROCESS_REQUEST, actionTypes.LIST_PROJECT_PROCESSES_REQUEST],
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

const cancelProcessReducers = combineReducers<CancelProcessState>({
    running: makeLoadingReducer(
        [actionTypes.CANCEL_PROCESS_REQUEST],
        [actionTypes.RESET_PROCESS, actionTypes.CANCEL_PROCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_PROCESS, actionTypes.CANCEL_PROCESS_REQUEST],
        [actionTypes.CANCEL_PROCESS_RESPONSE]
    ),
    response: (state = false, { type, error }: Action & { error?: {} }) => {
        switch (type) {
            case actionTypes.RESET_PROCESS:
                return false;
            case actionTypes.CANCEL_PROCESS_RESPONSE:
                return !error;
            default:
                return state;
        }
    }
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
    // TODO use RequestState<?>
    processesById, // TODO makeEntityByIdReducer
    paginatedProcessesById,

    loading,
    error: errorMsg,

    startProcess: startProcessReducers,
    cancelProcess: cancelProcessReducers,
    restoreProcess: restoreProcessReducers,
    cancelBulkProcess: cancelBulkProcessReducers,

    ansible: ansibleReducers,
    log: logReducers,
    poll: pollReducers,
    history: historyReducers,
    children: childrenReducers,
    attachments: attachmentReducers,
    events: eventsReducers
});

function* onGetProcess({ instanceId, includes }: GetProcessRequest) {
    try {
        const response = yield call(apiGet, instanceId, includes);
        yield put({
            type: actionTypes.PROCESS_DATA_RESPONSE,
            items: [response]
        });
    } catch (e) {
        yield handleErrors(actionTypes.PROCESS_DATA_RESPONSE, e);
    }
}

function* onProcessList({ orgName, projectName, filters, pagination }: ListProcessesRequest) {
    try {
        const response = yield call(apiProcessList, orgName, projectName, filters, pagination);
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
    profile
}: StartProcessRequest) {
    try {
        const response = yield call(apiStart, orgName, projectName, repoName, entryPoint, profile);
        yield put({
            type: actionTypes.START_PROCESS_RESPONSE,
            ...response
        });
    } catch (e) {
        yield handleErrors(actionTypes.START_PROCESS_RESPONSE, e);
    }
}

function* onCancelProcess({ instanceId }: CancelProcessRequest) {
    try {
        yield call(apiKill, instanceId);
        yield put({
            type: actionTypes.CANCEL_PROCESS_RESPONSE
        });
    } catch (e) {
        yield handleErrors(actionTypes.CANCEL_PROCESS_RESPONSE, e);
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

        yield put(pollActions.startProcessPolling(instanceId));
    } catch (e) {
        yield handleErrors(actionTypes.RESTORE_PROCESS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.GET_PROCESS_REQUEST, onGetProcess),
        throttle(1000, actionTypes.LIST_PROJECT_PROCESSES_REQUEST, onProcessList),
        takeLatest(actionTypes.START_PROCESS_REQUEST, onStartProcess),
        takeLatest(actionTypes.CANCEL_BULK_PROCESS_REQUEST, onCancelBulkProcess),
        takeLatest(actionTypes.CANCEL_PROCESS_REQUEST, onCancelProcess),
        takeLatest(actionTypes.RESTORE_PROCESS_REQUEST, onRestoreProcess),
        fork(logSagas),
        fork(pollSagas),
        fork(historySagas),
        fork(childrenSagas),
        fork(attachmentSagas),
        fork(eventsSagas),
        fork(ansibleSagas)
    ]);
};
