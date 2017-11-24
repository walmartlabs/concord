// @flow
import {call, fork, put, takeLatest, all} from "redux-saga/effects";
import {push as pushHistory} from "react-router-redux";
import types from "./actions";
import * as api from "../api";

function* startProcess(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.start, action.entryPoint);
        yield put({
            type: types.PROCESS_PORTAL_START_RESPONSE,
            response
        });

        const path = {
            pathname: `/process/${response.instanceId}/wizard`,
            query: {fullScreen: true}
        };
        yield put(pushHistory(path));
    } catch (e) {
        yield put({
            type: types.PROCESS_PORTAL_START_RESPONSE,
            error: true,
            message: e.message || "Error while starting a process"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield all([
        fork(takeLatest, types.PROCESS_PORTAL_START_REQUEST, startProcess)
    ]);
}