// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import types from "./actions";
import * as api from "./api";

function* loadData(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.loadData);
        yield put({
            type: types.PROCESS_HISTORY_RESPONSE,
            response
        });
    } catch (e) {
        yield put({
            type: types.PROCESS_HISTORY_RESPONSE,
            error: true,
            message: e.message || "Error while loading data"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, types.PROCESS_HISTORY_REQUEST, loadData)
    ];
}
