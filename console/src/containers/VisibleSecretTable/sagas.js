// @flow
import {call, put, select, fork, takeLatest} from "redux-saga/effects";
import {makeListFetcher} from "../../sagas/common";
import * as secretApi from "../../api/secret";
import {getSecretListState as getState} from "../../reducers";
import {getLastQuery} from "./reducers";
import {actionTypes} from "./actions";

const fetchSecretList = makeListFetcher("fetchSecretList", secretApi.fetchSecretList,
    actionTypes.FETCH_SECRET_LIST_RESULT);

function* deleteSecret(action: any): any {
    try {
        yield call(secretApi.deleteSecret, action.name);
        yield put({
            type: actionTypes.DELETE_SECRET_RESULT,
            name: action.name
        });

        const query = yield select((state) => getLastQuery(getState(state)));
        yield put({
            type: actionTypes.FETCH_SECRET_LIST_REQUEST,
            ...query
        });
    } catch (e) {
        console.error("deleteSecret -> error", e);
        yield put({
            type: actionTypes.DELETE_SECRET_RESULT,
            name: action.name,
            error: true,
            message: e.message || "Error while removing a secret"
        });
    }
}

export default function* (): Generator<*, *, *> {
    yield [
        fork(takeLatest, actionTypes.FETCH_SECRET_LIST_REQUEST, fetchSecretList),
        fork(takeLatest, actionTypes.DELETE_SECRET_REQUEST, deleteSecret),
    ];
}
