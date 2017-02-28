// @flow
import {call, put} from "redux-saga/effects";
import {SubmissionError} from "redux-form";
import {push as pushHistory} from "react-router-redux";
import * as projectApi from "../../api/project";
import {actionTypes} from "./actions";
import * as routes from "../../routes";

export function* fetchProjectData(action: any): Generator<*, *, *> {
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

export function* updateProjectData(action: any): Generator<*, *, *> {
    try {
        const response = yield call(projectApi.updateProject, action.name, action.data);
        action.resolve(response);
    } catch (e) {
        console.error("updateProjectData -> error", e);
        action.reject(new SubmissionError({_error: e.message}));
    }
}

export function* createProject(action: any): Generator<*, *, *> {
    try {
        const response = yield call(projectApi.createProject, action.data);
        action.resolve(response);
        yield put(pushHistory(routes.getProjectPath(action.data.name)));
    } catch (e) {
        console.error("createProject -> error", e);
        action.reject(new SubmissionError({_error: e.message}));
    }
}
