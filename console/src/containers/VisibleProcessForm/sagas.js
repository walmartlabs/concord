// @flow
import {call, fork, put, takeLatest} from "redux-saga/effects";
import {push as pushHistory} from "react-router-redux";
import {actionTypes} from "./actions";
import * as formApi from "../../api/processForm";
import {getProcessWizardPath} from "../../routes";

function* fetchForm(action: any): Generator<*, *, *> {
    try {
        const response = yield call(formApi.fetchForm, action.processInstanceId, action.formInstanceId);
        yield put({
            type: actionTypes.FETCH_PROCESS_FORM_RESULT,
            response
        });
    } catch (e) {
        yield put({
            type: actionTypes.FETCH_PROCESS_FORM_RESULT,
            error: true,
            message: e.message || "Error while loading a process form"
        });
    }
}

function* submitForm(action: any): Generator<*, *, *> {
    try {
        const response = yield call(formApi.submitForm, action.processInstanceId, action.formInstanceId, action.data);
        yield put({
            type: actionTypes.SUBMIT_PROCESS_FORM_RESULT,
            response
        });

        if (response.ok && action.wizard) {
            const path = {
                pathname: getProcessWizardPath(action.processInstanceId),
                query: {fullScreen: true, wizard: true}
            };
            yield put(pushHistory(path));
        }
    } catch (e) {
        yield put({
            type: actionTypes.SUBMIT_PROCESS_FORM_RESULT,
            error: true,
            message: e.message || "Error while submitting a process form"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield [
        fork(takeLatest, actionTypes.FETCH_PROCESS_FORM_REQUEST, fetchForm),
        fork(takeLatest, actionTypes.SUBMIT_PROCESS_FORM_REQUEST, submitForm)
    ];
}