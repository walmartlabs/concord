import {call, put, takeLatest, select} from "redux-saga/effects";
import {delay} from "redux-saga";
import * as api from "./api";
import actionTypes from "./actions/actionTypes";
import {getHistoryLastQuery} from "./reducers";

// history table - data

function* fetchHistoryData(action) {
    try {
        const response = yield call(api.fetchHistory, action.sortBy, action.sortDir);
        yield put({
            type: actionTypes.history.FETCH_HISTORY_DATA_RESULT,
            response
        });
    } catch (e) {
        console.error("fetchHistoryData -> error", e);
        yield put({
            type: actionTypes.history.FETCH_HISTORY_DATA_RESULT,
            error: true,
            message: e.message || "Error while loading process history"
        });
    }
}

// history table - killing running processes

function* killProc(action) {
    try {
        yield call(api.killProc, action.id);
        yield put({
            type: actionTypes.history.KILL_PROC_RESULT,
            id: action.id
        });

        const query = yield select(getHistoryLastQuery);
        yield call(delay, 2000);
        yield put({
            type: actionTypes.history.FETCH_HISTORY_DATA_REQUEST,
            ...query
        });
    } catch (e) {
        console.error("killProc -> error", e);
        yield put({
            type: actionTypes.history.KILL_PROC_RESULT,
            id: action.id,
            error: true,
            message: e.message || "Error while killing a process"
        });
    }
}

// log viewer

function* fetchLogData(action) {
    try {
        const status = yield call(api.fetchProcessStatus, action.instanceId);
        const response = yield call(api.fetchLog, status.logFileName, action.fetchRange);

        yield put({
            type: actionTypes.log.FETCH_LOG_DATA_FAILURE,
            ...response,
            status: status.status
        });
    } catch (e) {
        console.error("fetchLogData -> error", e);
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