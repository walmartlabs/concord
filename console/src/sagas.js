import {call, put, takeLatest, select, fork} from "redux-saga/effects";
import {delay} from "redux-saga";
import {push as pushHistory} from "react-router-redux";
import {SubmissionError} from "redux-form";
import * as api from "./api";
import actionTypes from "./actions/actionTypes";
import {getHistoryLastQuery, getProjectListLastQuery} from "./reducers";
import * as routes from "./routes";

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
        yield call(api.deleteProject, action.name);
        yield put({
            type: actionTypes.projectList.DELETE_PROJECT_RESULT,
            name: action.name
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
            name: action.name,
            error: true,
            message: e.message || "Error while removing a project"
        });
    }
};

// project - data

function* fetchProjectData(action) {
    try {
        const response = yield call(api.fetchProject, action.name);
        yield put({
            type: actionTypes.project.FETCH_PROJECT_RESULT,
            response
        });
    } catch (e) {
        console.error("fetchProjectData -> error", e);
        yield put({
            type: actionTypes.project.FETCH_PROJECT_RESULT,
            error: true,
            message: e.message || "Error while loading a project"
        });
    }
}

function* updateProjectData(action) {
    try {
        const response = yield call(api.updateProject, action.name, action.data);
        action.resolve(response);
    } catch (e) {
        console.error("updateProjectData -> error", e);
        action.reject(new SubmissionError({_error: e.message}));
    }
}

function* createProject(action) {
    try {
        const response = yield call(api.createProject, action.data);
        action.resolve(response);
        yield put(pushHistory(routes.getProjectPath(action.data.name)));
    } catch (e) {
        console.error("createProject -> error", e);
        action.reject(new SubmissionError({_error: e.message}));
    }
}

// templates - data

const fetchTemplateList = makeListFetcher("fetchTemplateList", api.fetchTemplateList,
    actionTypes.templateList.FETCH_TEMPLATE_LIST_RESULT);

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
            message: e.message || "Error while loading a log file"
        });
    }
}

function* saga() {
    // history
    yield fork(takeLatest, actionTypes.history.FETCH_HISTORY_DATA_REQUEST, fetchHistoryData);
    yield fork(takeLatest, actionTypes.history.KILL_PROC_REQUEST, killProc);

    // project list
    yield fork(takeLatest, actionTypes.projectList.FETCH_PROJECT_LIST_REQUEST, fetchProjectList);
    yield fork(takeLatest, actionTypes.projectList.DELETE_PROJECT_REQUEST, deleteProject);

    // project
    yield fork(takeLatest, actionTypes.project.FETCH_PROJECT_REQUEST, fetchProjectData);
    yield fork(takeLatest, actionTypes.project.UPDATE_PROJECT_REQUEST, updateProjectData);
    yield fork(takeLatest, actionTypes.project.CREATE_PROJECT_REQUEST, createProject);

    // template list
    yield fork(takeLatest, actionTypes.templateList.FETCH_TEMPLATE_LIST_REQUEST, fetchTemplateList);

    // log
    yield fork(takeLatest, actionTypes.log.FETCH_LOG_DATA_REQUEST, fetchLogData);
}

export default saga;