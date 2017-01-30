import {call, put, takeLatest, select} from "redux-saga/effects";
import {delay} from "redux-saga";
import * as api from "./api";
import actionTypes from "./actions/actionTypes";
import {getHistoryLastQuery} from "./reducers";

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

function* killProc(action) {
    try {
        yield call(api.killProc, action.id);
        yield put({
            type: actionTypes.history.KILL_PROC_SUCCESS,
            id: action.id
        });

        const query = yield select(getHistoryLastQuery);
        yield call(delay, 2000);
        yield put({
            type: actionTypes.history.FETCH_HISTORY_DATA_REQUEST,
            ...query
        });
    } catch (e) {
        yield put({
            type: actionTypes.history.FETCH_HISTORY_DATA_FAILURE,
            id: action.id,
            error: true,
            message: e.message || "Error while killing a process"
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
    yield takeLatest(actionTypes.history.KILL_PROC_REQUEST, killProc);
    yield takeLatest(actionTypes.log.FETCH_LOG_DATA_REQUEST, fetchLogData);
}

export default saga;