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

import { push as pushHistory } from 'react-router-redux';
import { combineReducers, Reducer } from 'redux';
import { all, call, put, select, takeLatest } from 'redux-saga/effects';

import { logout as apiLogout } from '../../api/service/console';
import { actions as login } from '../../components/organisms/Login';

import { CheckAuthAction, Logout, SetCurrentSessionAction, State, UserInfo } from './types';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'session';

const actionTypes = {
    CHECK_AUTH: `${NAMESPACE}/checkAuth`,
    LOGOUT: `${NAMESPACE}/logout`,
    SET_CURRENT_SESSION: `${NAMESPACE}/setCurrent`
};

export const actions = {
    setCurrent: (data: UserInfo): SetCurrentSessionAction => ({
        type: actionTypes.SET_CURRENT_SESSION,
        ...data
    }),

    checkAuth: (): CheckAuthAction => ({
        type: actionTypes.CHECK_AUTH
    }),

    logout: (): Logout => ({
        type: actionTypes.LOGOUT
    })
};

const userReducer: Reducer<UserInfo> = (state = {}, { type, ...rest }: SetCurrentSessionAction) => {
    switch (type) {
        case actionTypes.SET_CURRENT_SESSION:
            return rest;
        default:
            return state;
    }
};

export const reducers = combineReducers<State>({
    user: userReducer
});

export const selectors = {
    isLoggedIn: (state: State) => !!state.user.username
};

function* onCheckAuth() {
    const isLoggedIn = yield select(({ session }) => selectors.isLoggedIn(session));

    if (isLoggedIn) {
        return;
    }

    yield put(login.doRefresh());
}

function* onLogout() {
    try {
        yield call(apiLogout);
        yield put(actions.setCurrent({}));
        yield put(pushHistory('/login'));
    } catch (error) {
        throw Error(`Logout error: ${error}`);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.CHECK_AUTH, onCheckAuth),
        takeLatest(actionTypes.LOGOUT, onLogout)
    ]);
};
