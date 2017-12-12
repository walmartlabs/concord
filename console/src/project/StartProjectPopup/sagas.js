// @flow
import {call, fork, put, takeLatest, all} from "redux-saga/effects";
import * as api from "./api";
import types from "./actions";

function* startProject({repositoryId}: any): Generator<*, *, *> {
    try {
        const response = yield call(api.startProject, repositoryId);
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
    yield all([
        fork(takeLatest, types.PROJECT_START_REQUEST, startProject)
    ]);
}
