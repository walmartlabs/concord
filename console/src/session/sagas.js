// @flow
import {call, fork, put, select, takeEvery} from "redux-saga/effects";
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
        yield put(replaceHistory("/login"));
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
    yield [
        fork(takeEvery, types.CHECK_AUTH, checkAuth),
        fork(takeEvery, types.LOGOUT, doLogout)
    ];
}
