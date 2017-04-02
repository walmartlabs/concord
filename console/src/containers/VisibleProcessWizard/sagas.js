// @flow
import {delay} from "redux-saga";
import {call, fork, put, takeLatest} from "redux-saga/effects";
import {push as pushHistory} from "react-router-redux";
import {actionTypes} from "./actions";
import * as processApi from "../../api/process";
import * as formApi from "../../api/processForm";
import {getProcessFormPath, getProcessPath} from "../../routes";
import * as constants from "../VisibleProcessPage/constants";

function* nextForm({processInstanceId}): Generator<*, *, *> {
    try {
        let forms = [];

        while (true) {
            forms = yield call(formApi.listForms, processInstanceId);
            if (forms && forms.length > 0) {
                break;
            }

            const {status} = yield call(processApi.fetchStatus, processInstanceId);
            if (!constants.activeStatuses.includes(status)) {
                yield put(pushHistory(getProcessPath(processInstanceId)));
                return;
            }

            yield call(delay, 2000);
        }

        const {formInstanceId} = forms[0];
        const path = {
            pathname: getProcessFormPath(processInstanceId, formInstanceId),
            query: {fullScreen: true, wizard: true}
        };
        yield put(pushHistory(path));
    } catch (e) {
        yield put({
            type: actionTypes.CANCEL_PROCESS_WIZARD,
            error: true,
            message: e.message || "Error while loading a process"
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield fork(takeLatest, actionTypes.SHOW_NEXT_PROCESS_FORM, nextForm);
}