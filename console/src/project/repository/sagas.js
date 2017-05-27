// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import * as api from "./api";
import types from "./actions";

function* testRepo(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.testRepository, action.data);
        if (response.error) {
            yield put({
                type: types.REPOSITORY_TEST_RESPONSE,
                error: true,
                message: response.message || "Error while testing a repository"
            });
        } else {
            yield put({
                type: types.REPOSITORY_TEST_RESPONSE,
                response
            });
        }
    } catch (e) {
        yield put({
            type: types.REPOSITORY_TEST_RESPONSE,
            error: true,
            message: e.message || "Error while testing a repository"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, types.REPOSITORY_TEST_REQUEST, testRepo)
    ];
}
