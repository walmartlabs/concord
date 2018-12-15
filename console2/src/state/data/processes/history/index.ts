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
import { ConcordId } from '../../../../api/common';
import { GetProcessHistory, GetProcessHistoryState, State } from './types';
import { combineReducers } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';
import { get as apiGet } from '../../../../api/process/history';
import {
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer
} from '../../common';

const NAMESPACE = 'processes/history';

const actionTypes = {
    GET_PROCESS_HITORY_REQUEST: `${NAMESPACE}/request`,
    GET_PROCESS_HITORY_RESPONSE: `${NAMESPACE}/response`
};

export const actions = {
    getProcessHistory: (instanceId: ConcordId): GetProcessHistory => ({
        type: actionTypes.GET_PROCESS_HITORY_REQUEST,
        instanceId
    })
};

const getReducers = combineReducers<GetProcessHistoryState>({
    running: makeLoadingReducer(
        [actionTypes.GET_PROCESS_HITORY_REQUEST],
        [actionTypes.GET_PROCESS_HITORY_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.GET_PROCESS_HITORY_REQUEST],
        [actionTypes.GET_PROCESS_HITORY_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.GET_PROCESS_HITORY_RESPONSE)
});

export const reducers = combineReducers<State>({
    getHistory: getReducers
});

export const selectors = {
    processHistory: (state: State) => {
        const p = state.getHistory.response ? state.getHistory.response.items : [];
        return p;
    }
};

function* onGet({ instanceId }: GetProcessHistory) {
    try {
        const response = yield call(apiGet, instanceId);
        yield put({
            type: actionTypes.GET_PROCESS_HITORY_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.GET_PROCESS_HITORY_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.GET_PROCESS_HITORY_REQUEST, onGet)]);
};
