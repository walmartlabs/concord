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

import { combineReducers } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';

import { getActivity as apiGetActivity } from '../../../api/service/console/user';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import { GetUserActivityState, State, UserActivityDataRequest } from './types';

export { State };

const NAMESPACE = 'userActivity';

const actionTypes = {
    USER_ACTIVITY_REQUEST: `${NAMESPACE}/request`,
    USER_ACTIVITY_RESPONSE: `${NAMESPACE}/response`
};

export const actions = {
    getUserActivity: (
        maxProjectsPerOrg: number,
        maxOwnProcesses: number
    ): UserActivityDataRequest => ({
        type: actionTypes.USER_ACTIVITY_REQUEST,
        maxProjectsPerOrg,
        maxOwnProcesses
    })
};

const getUserActivityReducer = combineReducers<GetUserActivityState>({
    running: makeLoadingReducer(
        [actionTypes.USER_ACTIVITY_REQUEST],
        [actionTypes.USER_ACTIVITY_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.USER_ACTIVITY_REQUEST],
        [actionTypes.USER_ACTIVITY_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.USER_ACTIVITY_RESPONSE)
});

const loading = makeLoadingReducer(
    [actionTypes.USER_ACTIVITY_REQUEST],
    [actionTypes.USER_ACTIVITY_RESPONSE]
);

const errorMsg = makeErrorReducer(
    [actionTypes.USER_ACTIVITY_REQUEST],
    [actionTypes.USER_ACTIVITY_RESPONSE]
);

export const reducers = combineReducers<State>({
    loading,
    error: errorMsg,

    getUserActivity: getUserActivityReducer
});

function* onGet({ maxProjectsPerOrg }: UserActivityDataRequest) {
    try {
        const response = yield call(apiGetActivity, maxProjectsPerOrg);
        yield put({
            type: actionTypes.USER_ACTIVITY_RESPONSE,
            activity: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.USER_ACTIVITY_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.USER_ACTIVITY_REQUEST, onGet)]);
};
