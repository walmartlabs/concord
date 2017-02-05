import {call, put, takeLatest, select} from "redux-saga/effects";
import {delay} from "redux-saga";
import * as api from "./api";
import actionTypes from "./actions/actionTypes";
import {getHistoryLastQuery, getProjectListLastQuery} from "./reducers";

// common

const makeListFetcher = (name, apiCall, resultActionType) => function*(action) {
    try {
        const response = yield call(apiCall, action.sortBy, action.sortDir);
        yield put({
            type: resultActionType,
            response
        });
    } catch (e) {
        console.error("%s -> error", name, e);
        yield put({
            type: resultActionType,
            error: true,
            message: e.message || "Error while loading data"
        });
    }
};

// history table - data

const fetchHistoryData = makeListFetcher("fetchHistoryData", api.fetchHistory,
    actionTypes.history.FETCH_HISTORY_DATA_RESULT);

// history table - killing running processes

function* killProc(action) {
    try {
        const query = yield select(getHistoryLastQuery);

        yield call(api.killProc, action.id);

        // TODO make this operation sync instead?
        yield call(delay, 2000);

        yield put({
            type: actionTypes.history.KILL_PROC_RESULT,
            id: action.id
        });
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

// project table - data

const fetchProjectList = makeListFetcher("fetchProjectList", api.fetchProjectList,
    actionTypes.projectList.FETCH_PROJECT_LIST_RESULT);

function* deleteProject(action) {
    try {
        yield call(api.deleteProject, action.id);
        yield put({
            type: actionTypes.projectList.DELETE_PROJECT_RESULT,
            id: action.id
        });

        const query = yield select(getProjectListLastQuery);
        yield put({
            type: actionTypes.projectList.FETCH_PROJECT_LIST_REQUEST,
            ...query
        });
    } catch (e) {
        console.error("deleteProject -> error", e);
        yield put({
            type: actionTypes.history.DELETE_PROJECT_RESULT,
            id: action.id,
            error: true,
            message: e.message || "Error while removing a project"
        });
    }
};

// log viewer

function* fetchLogData(action) {
    try {
        const status = yield call(api.fetchProcessStatus, action.instanceId);
        const response = yield call(api.fetchLog, status.logFileName, action.fetchRange);

        yield put({
            type: actionTypes.log.FETCH_LOG_DATA_RESULT,
            ...response,
            status: status.status
        });
    } catch (e) {
        console.error("fetchLogData -> error", e);
        yield put({
            type: actionTypes.log.FETCH_LOG_DATA_RESULT,
            error: true,
            message: e.message || "Error while loading a log data"
        });
    }
}

function* saga() {
    // history
    yield takeLatest(actionTypes.history.FETCH_HISTORY_DATA_REQUEST, fetchHistoryData);
    yield takeLatest(actionTypes.history.KILL_PROC_REQUEST, killProc);

    // project list
    yield takeLatest(actionTypes.projectList.FETCH_PROJECT_LIST_REQUEST, fetchProjectList);
    yield takeLatest(actionTypes.projectList.DELETE_PROJECT_REQUEST, deleteProject);

    // log
    yield takeLatest(actionTypes.log.FETCH_LOG_DATA_REQUEST, fetchLogData);
}

export default saga;