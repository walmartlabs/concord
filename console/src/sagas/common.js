// @flow
import {call, put} from "redux-saga/effects";

export const makeListFetcher = (name: string, apiCall: Function, resultActionType: string) => function*(action: any): Generator<*, *, *> {
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
