// @flow
import {delay} from "redux-saga";
import {call, fork, put, takeLatest} from "redux-saga/effects";
import {push as pushHistory, replace as replaceHistory} from "react-router-redux";
import types from "./actions";
import * as processApi from "../api";
import * as formApi from "../form/api";
import * as constants from "../constants";

const STATUS_REFRESH_DELAY = 250;

function* nextForm({instanceId}): Generator<*, *, *> {
    try {
        let forms = [];

        while (true) {
            forms = yield call(formApi.listForms, instanceId);
            if (forms && forms.length > 0) {
                break;
            }

            const {status} = yield call(processApi.fetchStatus, instanceId);

            const stopped = !constants.activeStatuses.includes(status) &&
                constants.status.suspendedStatus !== status;

            if (stopped) {
                yield put(pushHistory(`/process/${instanceId}`));
                return;
            }

            yield call(delay, STATUS_REFRESH_DELAY);
        }

        const {formInstanceId, custom} = forms[0];

        if (custom) {
            // a form with branding
            let {uri} = yield call(formApi.startSession, instanceId, formInstanceId);

            // we can't proxy html resources using create-react-app
            // so we have to use another server to serve our custom forms
            // this is only for the development
            if (process.env.NODE_ENV !== "production") {
                uri = "http://localhost:8080" + uri;
            }

            window.location.replace(uri);
        } else {
            // regular form
            const path = {
                pathname: `/process/${instanceId}/form/${formInstanceId}`,
                query: {fullScreen: true, wizard: true}
            };
            yield put(replaceHistory(path));
        }
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