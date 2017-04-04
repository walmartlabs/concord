// @flow
import {call, put, select, fork, takeLatest} from "redux-saga/effects";
import {makeListFetcher} from "../../sagas/common";
import * as keypairApi from "../../api/keypair";
import {getKeypairListState as getState} from "../../reducers";
import {getLastQuery} from "./reducers";
import {actionTypes} from "./actions";

const fetchKeypairList = makeListFetcher("fetchKeypairList", keypairApi.fetchKeypairList,
    actionTypes.FETCH_KEYPAIR_LIST_RESULT);

function* deleteKeypair(action: any): any {
    try {
        yield call(keypairApi.deleteKeypair, action.name);
        yield put({
            type: actionTypes.DELETE_KEYPAIR_RESULT,
            name: action.name
        });

        const query = yield select((state) => getLastQuery(getState(state)));
        yield put({
            type: actionTypes.FETCH_KEYPAIR_LIST_REQUEST,
            ...query
        });
    } catch (e) {
        console.error("deleteKeypair -> error", e);
        yield put({
            type: actionTypes.DELETE_KEYPAIR_RESULT,
            name: action.name,
            error: true,
            message: e.message || "Error while removing a keypair"
        });
    }
}

export default function* (): Generator<*, *, *> {
    yield [
        fork(takeLatest, actionTypes.FETCH_KEYPAIR_LIST_REQUEST, fetchKeypairList),
        fork(takeLatest, actionTypes.DELETE_KEYPAIR_REQUEST, deleteKeypair),
    ];
}
