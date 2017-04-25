// @flow
import {fork, call, put, takeLatest, select} from "redux-saga/effects";
import {push as pushHistory} from "react-router-redux";
import types from "./actions";
import {actions as session} from "../session";
import {selectors} from "../session";
import * as api from "./api";

function* doLogin(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.login, action.username, action.password);

        yield put({
            type: types.LOGIN_RESPONSE,
            ...response
        });

        yield put(session.setCurrent(response));

        const destination = yield select(({session}) => selectors.getDestination(session));
        if (destination) {
            yield put(pushHistory(destination));
        } else {
            yield put(pushHistory("/"));
        }
    } catch (e) {
        let msg = e.message || "Log in error";
        if (e.status === 401) {
            msg = "Invalid username and/or password";
        }

        yield put({
            type: types.LOGIN_RESPONSE,
            error: true,
            message: msg
        });
    }
}

function* doRefresh(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.login);
        yield put(session.setCurrent(response));
    } catch (e) {
        yield put(pushHistory("/login"));
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, types.LOGIN_REQUEST, doLogin),
        fork(takeLatest, types.LOGIN_REFRESH, doRefresh)
    ];
}
