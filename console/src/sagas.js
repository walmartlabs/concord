import {call, put, takeLatest} from "redux-saga/effects";
import * as api from "./api";
import actionTypes from "./actions/actionTypes";

function* fetchHistoryData(action) {
    try {
        const response = yield call(api.fetchHistory, action.sortBy, action.sortDir);
        yield put({
            type: actionTypes.history.FETCH_HISTORY_DATA_SUCCESS,
            response
        });
    } catch (e) {
        yield put({
            type: actionTypes.history.FETCH_HISTORY_DATA_FAILURE,
            error: true,
            message: e.message || "Error while loading process history"
        });
    }
}

function* fetchLogData(action) {
    try {
        const response = yield call(api.fetchLog, action.fileName);
        yield put({
            type: actionTypes.log.FETCH_LOG_DATA_SUCCESS,
            response
        });
    } catch (e) {
        yield put({
            type: actionTypes.log.FETCH_LOG_DATA_FAILURE,
            error: true,
            message: e.message || "Error while loading a log data"
        });
    }
}

function* saga() {
    yield takeLatest(actionTypes.history.FETCH_HISTORY_DATA_REQUEST, fetchHistoryData);
    yield takeLatest(actionTypes.log.FETCH_LOG_DATA_REQUEST, fetchLogData);
}

export default saga;