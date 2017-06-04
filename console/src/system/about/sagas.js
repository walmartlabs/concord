// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import * as api from "./api";
import types from "./actions";

function* loadInfo(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.loadServerVersion);
        yield put({
            type: types.ABOUT_INFO_RESPONSE,
            response
        });
    } catch (e) {
        yield put({
            type: types.ABOUT_INFO_RESPONSE,
            error: true,
            message: e.message || "Error while loading data"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, types.ABOUT_INFO_REQUEST, loadInfo)
    ];
}
