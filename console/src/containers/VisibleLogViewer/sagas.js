// @flow
import {call, put} from "redux-saga/effects";
import * as processApi from "../../api/process";
import * as logApi from "../../api/log";
import {actionTypes} from "./actions";

export function* fetchLogData(action: any): Generator<*, *, *> {
    try {
        const status = yield call(processApi.fetchProcessStatus, action.instanceId);
        const response = yield call(logApi.fetchLog, status.logFileName, action.fetchRange);

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
