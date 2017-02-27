// @flow
import {call, put} from "redux-saga/effects";
import * as api from "../../api";
import {actionTypes} from "./actions";

export function* fetchLogData(action: any): Generator<*, *, *> {
    try {
        const status = yield call(api.fetchProcessStatus, action.instanceId);
        const response = yield call(api.fetchLog, status.logFileName, action.fetchRange);

        yield put({
            type: actionTypes.FETCH_LOG_DATA_RESULT,
            ...response,
            status: status.status
        });
    } catch (e) {
        console.error("fetchLogData -> error", e);
        yield put({
            type: actionTypes.FETCH_LOG_DATA_RESULT,
            error: true,
            message: e.message || "Error while loading a log file"
        });
    }
}
