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
import { Action, combineReducers } from 'redux';
import { all, call, put, throttle } from 'redux-saga/effects';

import { findUsers as apiFindUsers } from '../../../api/service/console';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import { SearchUsersRequest, SearchUsersState, State } from './types';

export { State };

const NAMESPACE = 'search';

const actionTypes = {
    FIND_USERS_REQUEST: `${NAMESPACE}/users/request`,
    FIND_USERS_RESPONSE: `${NAMESPACE}/users/response`,
    FIND_USERS_RESET: `${NAMESPACE}/users/reset`
};

export const actions = {
    findUsers: (filter: string): SearchUsersRequest => ({
        type: actionTypes.FIND_USERS_REQUEST,
        filter
    }),

    resetUserSearch: (): Action => ({
        type: actionTypes.FIND_USERS_RESET
    })
};

const usersReducers = combineReducers<SearchUsersState>({
    running: makeLoadingReducer(
        [actionTypes.FIND_USERS_REQUEST],
        [actionTypes.FIND_USERS_RESET, actionTypes.FIND_USERS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.FIND_USERS_RESET, actionTypes.FIND_USERS_REQUEST],
        [actionTypes.FIND_USERS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.FIND_USERS_RESPONSE, actionTypes.FIND_USERS_RESET)
});

export const reducers = combineReducers<State>({
    users: usersReducers
});

function* onFindUsers({ filter }: SearchUsersRequest) {
    if (filter.length < 5) {
        // ignore short values
        yield put({
            type: actionTypes.FIND_USERS_RESPONSE,
            items: []
        });

        return;
    }

    try {
        const response = yield call(apiFindUsers, filter);
        yield put({
            type: actionTypes.FIND_USERS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.FIND_USERS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([throttle(2000, actionTypes.FIND_USERS_REQUEST, onFindUsers)]);
};
