// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import * as api from "./api";
import types from "./actions";

function* createNewKeyPair(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.createNewKeyPair);
        yield put({
            type: types.USER_SECRET_CREATE_KEYPAIR,
            response
        });
    } catch (e) {
        yield put({
            type: types.USER_SECRET_CREATE_KEYPAIR_RESPONSE,
            error: true,
            message: e.message || "Error while creating new keypair"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, types.USER_SECRET_CREATE_KEYPAIR, createNewKeyPair)
    ];
}
