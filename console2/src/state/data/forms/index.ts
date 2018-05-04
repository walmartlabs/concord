/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
import { combineReducers } from 'redux';
import { delay } from 'redux-saga';
import { all, call, put, takeLatest } from 'redux-saga/effects';
import { push as pushHistory, replace as replaceHistory } from 'react-router-redux';

import { ConcordId } from '../../../api/common';
import { get as apiGet, submit as apiSubmit, list as apiList } from '../../../api/process/form';
import { get as apiGetProcess, isFinal } from '../../../api/process';
import { startSession as apiStartSession } from '../../../api/service/custom_form';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import {
    FormDataType,
    GetProcessFormRequest,
    GetProcessFormState,
    ProcessWizardState,
    StartProcessWizard,
    State,
    SubmitProcessFormRequest,
    SubmitProcessFormState
} from './types';

export { State };

const NAMESPACE = 'forms';

const actionTypes = {
    GET_PROCESS_FORM_REQUEST: `${NAMESPACE}/get/request`,
    GET_PROCESS_FORM_RESPONSE: `${NAMESPACE}/get/response`,

    SUBMIT_PROCESS_FORM_REQUEST: `${NAMESPACE}/submit/request`,
    SUBMIT_PROCESS_FORM_RESPONSE: `${NAMESPACE}/submit/response`,

    START_PROCESS_WIZARD: `${NAMESPACE}/wizard/start`,
    STOP_PROCESS_WIZARD: `${NAMESPACE}/wizard/stop`,

    RESET_PROCESS_FORMS: `${NAMESPACE}/reset`
};

export const actions = {
    getProcessForm: (
        processInstanceId: ConcordId,
        formInstanceId: string
    ): GetProcessFormRequest => ({
        type: actionTypes.GET_PROCESS_FORM_REQUEST,
        processInstanceId,
        formInstanceId
    }),

    submitProcessForm: (
        processInstanceId: ConcordId,
        formInstanceId: string,
        wizard: boolean,
        yieldFlow: boolean,
        data: FormDataType
    ): SubmitProcessFormRequest => ({
        type: actionTypes.SUBMIT_PROCESS_FORM_REQUEST,
        processInstanceId,
        formInstanceId,
        wizard,
        yieldFlow,
        data
    }),

    startWizard: (processInstanceId: ConcordId): StartProcessWizard => ({
        type: actionTypes.START_PROCESS_WIZARD,
        processInstanceId
    }),

    reset: () => ({
        type: actionTypes.RESET_PROCESS_FORMS
    })
};

const getFormReducer = combineReducers<GetProcessFormState>({
    running: makeLoadingReducer(
        [actionTypes.GET_PROCESS_FORM_REQUEST],
        [actionTypes.GET_PROCESS_FORM_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.GET_PROCESS_FORM_REQUEST],
        [actionTypes.GET_PROCESS_FORM_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.GET_PROCESS_FORM_RESPONSE,
        actionTypes.RESET_PROCESS_FORMS
    )
});

const submitFormReducer = combineReducers<SubmitProcessFormState>({
    running: makeLoadingReducer(
        [actionTypes.SUBMIT_PROCESS_FORM_REQUEST],
        [actionTypes.SUBMIT_PROCESS_FORM_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.SUBMIT_PROCESS_FORM_REQUEST],
        [actionTypes.SUBMIT_PROCESS_FORM_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.SUBMIT_PROCESS_FORM_RESPONSE,
        actionTypes.RESET_PROCESS_FORMS
    )
});

const wizardReducer = combineReducers<ProcessWizardState>({
    error: makeErrorReducer([actionTypes.START_PROCESS_WIZARD], [actionTypes.STOP_PROCESS_WIZARD])
});

export const reducers = combineReducers<State>({
    get: getFormReducer,
    submit: submitFormReducer,
    wizard: wizardReducer
});

const updateForDev = (uri: string) => {
    if (process.env.NODE_ENV !== 'production') {
        uri = 'http://localhost:8080' + uri;
    }
    return uri;
};

function* onGetProcessForm({ processInstanceId, formInstanceId }: GetProcessFormRequest) {
    try {
        const response = yield call(apiGet, processInstanceId, formInstanceId);
        yield put({
            type: actionTypes.GET_PROCESS_FORM_RESPONSE,
            ...response
        });

        // TODO should it be a separate saga?
        if (response.custom) {
            let { uri } = yield call(apiStartSession, processInstanceId, formInstanceId);
            uri = updateForDev(uri);
            window.location.replace(uri);
        }
    } catch (e) {
        yield handleErrors(actionTypes.GET_PROCESS_FORM_RESPONSE, e);
    }
}

function* onSubmitProcessForm({
    processInstanceId,
    formInstanceId,
    wizard,
    yieldFlow,
    data
}: SubmitProcessFormRequest) {
    try {
        const response = yield call(apiSubmit, processInstanceId, formInstanceId, data);
        yield put({
            type: actionTypes.SUBMIT_PROCESS_FORM_RESPONSE,
            ...response
        });

        if (response.ok && wizard) {
            if (yieldFlow) {
                yield delay(1000);
                const path = {
                    pathname: `/process/${processInstanceId}`
                };
                yield put(pushHistory(path));
            } else {
                const path = {
                    pathname: `/process/${processInstanceId}/wizard`,
                    query: { fullScreen: true }
                };
                yield put(pushHistory(path));
            }
        }
    } catch (e) {
        yield handleErrors(actionTypes.SUBMIT_PROCESS_FORM_RESPONSE, e);
    }
}

function* onStartProcessWizard({ processInstanceId }: StartProcessWizard) {
    try {
        let forms;

        while (true) {
            forms = yield call(apiList, processInstanceId);
            if (forms && forms.length > 0) {
                break;
            }

            const { status } = yield call(apiGetProcess, processInstanceId);

            const stopped = isFinal(status);

            if (stopped) {
                yield put(pushHistory(`/process/${processInstanceId}`));
                return;
            }

            yield call(delay, 1000);
        }

        const { formInstanceId, custom } = forms[0];
        const yieldFlow = forms[0].yield;

        if (custom) {
            // a form with branding
            let { uri } = yield call(apiStartSession, processInstanceId, formInstanceId);

            // we can't proxy html resources using create-react-app
            // so we have to use another server to serve our custom forms
            // this is only for the development
            uri = updateForDev(uri);

            window.location.replace(uri);
        } else {
            // regular form
            const path = {
                pathname: `/process/${processInstanceId}/form/${formInstanceId}/wizard?fullScreen=true`,
                query: { fullScreen: true, yieldFlow }
            };
            yield put(replaceHistory(path));
        }
    } catch (e) {
        yield handleErrors(actionTypes.STOP_PROCESS_WIZARD, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.GET_PROCESS_FORM_REQUEST, onGetProcessForm),
        takeLatest(actionTypes.SUBMIT_PROCESS_FORM_REQUEST, onSubmitProcessForm),
        takeLatest(actionTypes.START_PROCESS_WIZARD, onStartProcessWizard)
    ]);
};
