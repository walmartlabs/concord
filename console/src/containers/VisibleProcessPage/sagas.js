// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import {actionTypes} from "./actions";
import * as processApi from "../../api/process";
import * as processFormApi from "../../api/processForm";
import * as processActions from "./actions";

function* fetchStatus(action: any): Generator<*, *, *> {
    try {
        if (action.includeForms) {
            const [status, forms] = yield [
                call(processApi.fetchStatus, action.instanceId),
                call(processFormApi.listForms, action.instanceId)
            ];

            yield put({
                type: actionTypes.FETCH_PROCESS_STATUS_RESULT,
                response: {
                    ...status,
                    forms: forms
                }
            });
        } else {
            // TODO do we really need to fetch process statuses w/o forms?
            const response = yield call(processApi.fetchStatus, action.instanceId);
            yield put({
                type: actionTypes.FETCH_PROCESS_STATUS_RESULT,
                response
            });
        }
    } catch (e) {
        yield put({
            type: actionTypes.FETCH_PROCESS_STATUS_RESULT,
            error: true,
            message: e.message || "Error while loading a process"
        });
    }
}

function* kill(action: any): Generator<*, *, *> {
    try {
        const response = yield call(processApi.killProc, action.instanceId);
        yield put({
            type: actionTypes.KILL_PROCESS_RESULT,
            response
        });

        yield put(processActions.fetchData(action.instanceId));
    } catch (e) {
        yield put({
            type: actionTypes.KILL_PROCESS_RESULT,
            error: true,
            message: e.message || "Error while killing a process"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, actionTypes.FETCH_PROCESS_STATUS_REQUEST, fetchStatus),
        fork(takeLatest, actionTypes.KILL_PROCESS_REQUEST, kill)
    ];
}