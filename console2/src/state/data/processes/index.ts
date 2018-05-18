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
import { Action, combineReducers, Reducer } from 'redux';
import { all, call, fork, put, takeLatest } from 'redux-saga/effects';

import { ConcordId, ConcordKey } from '../../../api/common';
import { list as apiOrgList } from '../../../api/org/process';
import { start as apiStart, kill as apiKill } from '../../../api/process';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import { reducers as logReducers, sagas as logSagas } from './logs';
import { reducers as pollReducers, sagas as pollSagas } from './poll';
import {
    CancelProcessRequest,
    CancelProcessState,
    ListProjectProcessesRequest,
    ProcessDataResponse,
    Processes,
    StartProcessRequest,
    StartProcessState,
    State
} from './types';

export { Processes, State };

const NAMESPACE = 'processes';

const actionTypes = {
    LIST_PROJECT_PROCESSES_REQUEST: `${NAMESPACE}/project/list/request`,
    PROCESS_DATA_RESPONSE: `${NAMESPACE}/data/response`,

    START_PROCESS_REQUEST: `${NAMESPACE}/start/request`,
    START_PROCESS_RESPONSE: `${NAMESPACE}/start/response`,

    CANCEL_PROCESS_REQUEST: `${NAMESPACE}/cancel/request`,
    CANCEL_PROCESS_RESPONSE: `${NAMESPACE}/cancel/response`,

    RESET_PROCESS: `${NAMESPACE}/reset`
};

export const actions = {
    listProjectProcesses: (
        orgName?: ConcordKey,
        projectName?: ConcordKey
    ): ListProjectProcessesRequest => ({
        type: actionTypes.LIST_PROJECT_PROCESSES_REQUEST,
        orgName,
        projectName
    }),

    startProcess: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        repoName: ConcordKey
    ): StartProcessRequest => ({
        type: actionTypes.START_PROCESS_REQUEST,
        orgName,
        projectName,
        repoName
    }),

    cancel: (instanceId: ConcordId): CancelProcessRequest => ({
        type: actionTypes.CANCEL_PROCESS_REQUEST,
        instanceId
    }),

    reset: () => ({
        type: actionTypes.RESET_PROCESS
    })
};

const processById: Reducer<Processes> = (
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

const loading = makeLoadingReducer(
    [actionTypes.LIST_PROJECT_PROCESSES_REQUEST],
    [actionTypes.PROCESS_DATA_RESPONSE]
);

const errorMsg = makeErrorReducer(
    [actionTypes.LIST_PROJECT_PROCESSES_REQUEST],
    [actionTypes.PROCESS_DATA_RESPONSE]
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

const cancelProcessReducers = combineReducers<CancelProcessState>({
    running: makeLoadingReducer(
        [actionTypes.CANCEL_PROCESS_REQUEST],
        [actionTypes.CANCEL_PROCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.CANCEL_PROCESS_REQUEST],
        [actionTypes.CANCEL_PROCESS_RESPONSE]
    ),
    response: (state = false, { type, error }: Action & { error?: {} }) => {
        switch (type) {
            case actionTypes.CANCEL_PROCESS_RESPONSE:
                return !error;
            default:
                return state;
        }
    }
});

export const reducers = combineReducers<State>({
    // TODO use RequestState<?>
    processById, // TODO makeEntityByIdReducer
    loading,
    error: errorMsg,

    startProcess: startProcessReducers,
    cancelProcess: cancelProcessReducers,

    log: logReducers,
    poll: pollReducers
});

function* onProjectList({ orgName, projectName }: ListProjectProcessesRequest) {
    try {
        const response = yield call(apiOrgList, orgName, projectName);
        yield put({
            type: actionTypes.PROCESS_DATA_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.PROCESS_DATA_RESPONSE, e);
    }
}

function* onStartProcess({ orgName, projectName, repoName }: StartProcessRequest) {
    try {
        const response = yield call(apiStart, orgName, projectName, repoName);
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

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.LIST_PROJECT_PROCESSES_REQUEST, onProjectList),
        takeLatest(actionTypes.START_PROCESS_REQUEST, onStartProcess),
        takeLatest(actionTypes.CANCEL_PROCESS_REQUEST, onCancelProcess),
        fork(logSagas),
        fork(pollSagas)
    ]);
};
