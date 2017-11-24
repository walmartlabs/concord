// @flow
import {call, put, fork, takeLatest, all} from "redux-saga/effects";
import * as process from "../api";
import * as log from "./api";
import types from "./actions";

function* loadData(action: any): Generator<*, *, *> {
    try {
        // TODO parallel?
        const status = yield call(process.fetchStatus, action.instanceId);
        const response = yield call(log.fetchLog, action.instanceId, action.fetchRange);

        yield put({
            type: types.PROCESS_LOG_RESPONSE,
            ...response,
            status: status.status
        });
    } catch (e) {
        console.error("fetchLogData -> error", e);
        yield put({
            type: types.PROCESS_LOG_RESPONSE,
            error: true,
            message: e.message || "Error while loading a log file"
        });
    }
}

export default function* (): Generator<*, *, *> {
    yield all([
        fork(takeLatest, types.PROCESS_LOG_REQUEST, loadData)
    ]);
}
