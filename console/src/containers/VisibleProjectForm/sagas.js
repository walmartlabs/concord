// @flow
import {call, put, fork, takeLatest} from "redux-saga/effects";
import {SubmissionError} from "redux-form";
import {push as pushHistory} from "react-router-redux";
import * as projectApi from "../../api/project";
import {actionTypes} from "./actions";
import * as routes from "../../routes";

function* fetchProjectData(action: any): Generator<*, *, *> {
    try {
        const response = yield call(projectApi.fetchProject, action.name);
        yield put({
            type: actionTypes.FETCH_PROJECT_RESULT,
            response
        });
    } catch (e) {
        console.error("fetchProjectData -> error", e);
        yield put({
            type: actionTypes.FETCH_PROJECT_RESULT,
            error: true,
            message: e.message || "Error while loading a project"
        });
    }
}

function* updateProjectData(action: any): Generator<*, *, *> {
    try {
        const response = yield call(projectApi.updateProject, action.name, action.data);
        action.resolve(response);
    } catch (e) {
        console.error("updateProjectData -> error", e);
        action.reject(new SubmissionError({_error: e.message}));
    }
}

function* createProject(action: any): Generator<*, *, *> {
    try {
        const response = yield call(projectApi.createProject, action.data);
        action.resolve(response);
        yield put(pushHistory(routes.getProjectPath(action.data.name)));
    } catch (e) {
        console.error("createProject -> error", e);
        action.reject(new SubmissionError({_error: e.message}));
    }
}

export default function* (): Generator<*, *, *> {
    yield [
        fork(takeLatest, actionTypes.FETCH_PROJECT_REQUEST, fetchProjectData),
        fork(takeLatest, actionTypes.UPDATE_PROJECT_REQUEST, updateProjectData),
        fork(takeLatest, actionTypes.CREATE_PROJECT_REQUEST, createProject),
    ];
}
