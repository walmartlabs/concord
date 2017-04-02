// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import {push as pushHistory} from "react-router-redux";
import {actionTypes} from "./actions";
import * as processApi from "../../api/process";
import {getProcessWizardPath} from "../../routes";

function* startProcess(action: any): Generator<*, *, *> {
    try {
        const response = yield call(processApi.start, action.entryPoint);
        yield put({
            type: actionTypes.START_PROCESS_RESULT,
            response
        });

        const path = {
            pathname: getProcessWizardPath(response.instanceId),
            query: {fullScreen: true, wizard: true}
        };
        yield put(pushHistory(path));
    } catch (e) {
        yield put({
            type: actionTypes.START_PROCESS_RESULT,
            error: true,
            message: e.message || "Error while starting a process"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, actionTypes.START_PROCESS_REQUEST, startProcess)
    ];
}