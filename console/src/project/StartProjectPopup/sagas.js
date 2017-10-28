// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import * as api from "./api";
import types from "./actions";

function* startProject(action: any): Generator<*, *, *> {
    try {
        const {projectName, repositoryName} = action.data;

        const response = yield call(api.startProject, projectName, repositoryName);
        if (response.error) {
            yield put({
                response,
                type: types.PROJECT_START_RESPONSE,
                error: true,
            });
        } else {
            yield put({
                type: types.PROJECT_START_RESPONSE,
                response
            });
        }
    } catch (e) {
        yield put({
            response: e,
            type: types.PROJECT_START_RESPONSE,
            error: true
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, types.PROJECT_START_REQUEST, startProject)
    ];
}
