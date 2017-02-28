// @flow
import {call, put, select} from "redux-saga/effects";
import {delay} from "redux-saga";
import {makeListFetcher} from "../../sagas/common";
import {actionTypes} from "./actions";
import {getHistoryState as getState} from "../../reducers";
import {getLastQuery} from "./reducers";
import * as historyApi from "../../api/history";
import * as processApi from "../../api/process";

export const fetchHistoryData = makeListFetcher("fetchHistoryData", historyApi.fetchRows,
    actionTypes.FETCH_HISTORY_DATA_RESULT);

export function* killProc(action: any): Generator<*, *, *> {
    try {
        const query = yield select((state) => getLastQuery(getState(state)));

        yield call(processApi.killProc, action.id);

        // TODO make this operation sync instead?
        yield call(delay, 2000);

        yield put({
            type: actionTypes.KILL_PROC_RESULT,
            id: action.id
        });
        yield put({
            type: actionTypes.FETCH_HISTORY_DATA_REQUEST,
            ...query
        });
    } catch (e) {
        console.error("killProc -> error", e);
        yield put({
            type: actionTypes.KILL_PROC_RESULT,
            id: action.id,
            error: true,
            message: e.message || "Error while killing a process"
        });
    }
}

