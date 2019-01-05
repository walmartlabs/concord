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
import { Action, combineReducers, Reducer } from 'redux';
import { all, call, put, select, takeLatest } from 'redux-saga/effects';
import { whoami as apiWhoami } from '../../../api/service/console';
import { actions as session } from '../../../state/session';

const NAMESPACE = 'login';

const actionTypes = {
    LOGIN_REQUEST: `${NAMESPACE}/request`,
    LOGIN_RESPONSE: `${NAMESPACE}/response`,
    LOGIN_REFRESH_REQUEST: `${NAMESPACE}/refresh/request`,
    LOGIN_REFRESH_RESPONSE: `${NAMESPACE}/refresh/response`
};

interface LoginAction extends Action {
    username: string;
    password: string;
    apiKey: string;
}

interface LoginResponse extends Action {
    status: number;
    error?: string;
}

type RefreshAction = Action;

export const actions = {
    doLogin: (username: string, password: string, apiKey: string): LoginAction => ({
        type: actionTypes.LOGIN_REQUEST,
        username,
        password,
        apiKey
    }),
    doRefresh: (): RefreshAction => ({
        type: actionTypes.LOGIN_REFRESH_REQUEST
    })
};

const loggingIn = (state = false, action: LoginAction | LoginResponse) => {
    switch (action.type) {
        case actionTypes.LOGIN_REQUEST:
        case actionTypes.LOGIN_REFRESH_REQUEST:
            return true;
        case actionTypes.LOGIN_RESPONSE:
        case actionTypes.LOGIN_REFRESH_RESPONSE:
            return false;
        default:
            return state;
    }
};

const error: Reducer<string | null, LoginResponse> = (state = null, action: LoginResponse): string | null => {
    switch (action.type) {
        case actionTypes.LOGIN_RESPONSE:
            return action.error ? action.error : null;
        default:
            return state;
    }
};

export interface State {
    loggingIn: boolean;
    error: string | null;
}

export const reducers: Reducer<State> = combineReducers({ loggingIn, error });

export const selectors = {
    isLoggingIn: (state: State): boolean => state.loggingIn,
    getError: (state: State) => state.error
};

const DEFAULT_DESTINATION = '/';

// TODO how are we going to type the route's state?
// tslint:disable-next-line
const getDestination = ({ router }: { router: any }) => {
    if (!router || !router.location || !router.location.state || !router.location.state.from) {
        return DEFAULT_DESTINATION;
    }

    const from = router.location.state.from;

    return from.pathname || DEFAULT_DESTINATION;
};

function* onLogin({ username, password, apiKey }: LoginAction) {
    try {
        const response = yield call(apiWhoami, username, password, apiKey);
        yield put({
            type: actionTypes.LOGIN_RESPONSE,
            ...response
        });

        yield put(session.setCurrent(response));

        // redirect to the original page
        const destination = yield select(getDestination);
        yield put(pushHistory(destination));
    } catch (e) {
        let msg = e.message || 'Log in error';
        if (e.status === 401) {
            msg = 'Invalid username and/or password';
        }

        yield put({
            type: actionTypes.LOGIN_RESPONSE,
            error: msg
        });
    }
}

function* onRefresh() {
    try {
        const response = yield call(apiWhoami);
        yield put(session.setCurrent(response));
        yield put({
            type: actionTypes.LOGIN_REFRESH_RESPONSE
        });
    } catch (e) {
        console.warn(e);
        yield put({
            type: actionTypes.LOGIN_REFRESH_RESPONSE
        });
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.LOGIN_REQUEST, onLogin),
        takeLatest(actionTypes.LOGIN_REFRESH_REQUEST, onRefresh)
    ]);
};
