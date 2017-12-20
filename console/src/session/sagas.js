/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
// @flow
import {call, fork, put, select, takeEvery, all} from "redux-saga/effects";
import {replace as replaceHistory} from "react-router-redux";
import Cookies from "js-cookie";
import types from "./actions";
import * as actions from "./actions";
import * as selectors from "./reducers";
import {actions as login} from "../login";
import * as api from "./api";

const SESSION_COOKIE_NAME = "JSESSIONID";

function* checkAuth({destination}): Generator<*, *, *> {
    const isLoggedIn = yield select(({session}) => selectors.isLoggedIn(session));
    if (isLoggedIn) {
        return;
    }

    // TODO use redux wrapper?
    const hasSessionCookie = Cookies.get(SESSION_COOKIE_NAME);
    if (hasSessionCookie) {
        yield put(login.doRefresh());
    } else {
        yield put(actions.update({params: {destination}}));

        // preserve the fullscreen toggle
        const fullScreen = destination && destination.query && destination.query.fullScreen;
        yield put(replaceHistory({pathname: "/login", query: {fullScreen}}));
    }
}

function* doLogout(): Generator<*, *, *> {
    try {
        yield call(api.logout);

        yield put(actions.setCurrent({}));

        // TODO use redux wrapper?
        Cookies.remove(SESSION_COOKIE_NAME);

        yield put(login.doRefresh());
    } catch (e) {
        console.error("Logout error", e);
    }
}

export default function*(): Generator<*, *, *> {
    yield all([
        fork(takeEvery, types.CHECK_AUTH, checkAuth),
        fork(takeEvery, types.LOGOUT, doLogout)
    ]);
}
