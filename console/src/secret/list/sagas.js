// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import * as api from "./api";
import types from "./actions";

function* fetchSecretList(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.fetchSecretList);
        yield put({
            type: types.USER_SECRET_LIST_RESPONSE,
            response
        });
    } catch (e) {
        yield put({
            type: types.USER_SECRET_LIST_RESPONSE,
            error: true,
            message: e.message || "Error while loading data"
        });
    }
}

function* deleteSecret(action: any): Generator<*, *, *> {
    try {
        yield call(api.deleteSecret, action.name);
        yield put({
            type: types.USER_SECRET_DELETE_RESPONSE,
            name: action.name
        });

        if (action.onSuccess) {
            for (const a of action.onSuccess) {
                yield put(a);
            }
        }
    } catch (e) {
        console.error("deleteSecret -> error", e);
        yield put({
            type: types.USER_SECRET_DELETE_RESPONSE,
            name: action.name,
            error: true,
            message: e.message || "Error while removing a secret"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, types.USER_SECRET_LIST_REQUEST, fetchSecretList),
        fork(takeLatest, types.USER_SECRET_DELETE_REQUEST, deleteSecret),
    ];
}
