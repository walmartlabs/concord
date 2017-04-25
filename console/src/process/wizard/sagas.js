// @flow
import {delay} from "redux-saga";
import {call, fork, put, takeLatest} from "redux-saga/effects";
import {push as pushHistory} from "react-router-redux";
import types from "./actions";
import * as processApi from "../api";
import * as formApi from "../form/api";
import * as constants from "../constants";

function* nextForm({instanceId}): Generator<*, *, *> {
    try {
        let forms = [];

        while (true) {
            forms = yield call(formApi.listForms, instanceId);
            if (forms && forms.length > 0) {
                break;
            }

            const {status} = yield call(processApi.fetchStatus, instanceId);
            if (!constants.activeStatuses.includes(status)) {
                yield put(pushHistory(`/process/${instanceId}`));
                return;
            }

            yield call(delay, 2000);
        }

        const {formInstanceId} = forms[0];
        const path = {
            pathname: `/process/${instanceId}/form/${formInstanceId}`,
            query: {fullScreen: true, wizard: true}
        };
        yield put(pushHistory(path));
    } catch (e) {
        yield put({
            type: types.PROCESS_WIZARD_CANCEL,
            instanceId,
            error: true,
            message: e.message || "Error while loading a process"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield fork(takeLatest, types.PROCESS_WIZARD_NEXT_FORM, nextForm);
}