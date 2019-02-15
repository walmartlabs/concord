/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import { push as pushHistory, replace as replaceHistory } from 'react-router-redux';
import { combineReducers } from 'redux';
import { delay } from 'redux-saga';
import { all, call, put, takeLatest } from 'redux-saga/effects';

import { ConcordId } from '../../../api/common';
import { isFinal, get as apiGetProcess } from '../../../api/process';
import { FormListEntry } from '../../../api/process/form';
import {
    FormInstanceEntry,
    get as apiGet,
    list as apiList,
    submit as apiSubmit
} from '../../../api/process/form';
import { startSession as apiStartSession } from '../../../api/service/custom_form';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import {
    FormDataType,
    GetProcessFormRequest,
    GetProcessFormState,
    ProcessWizardState,
    StartProcessForm,
    StartProcessWizard,
    State,
    SubmitProcessFormRequest,
    SubmitProcessFormState
} from './types';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'forms';

const actionTypes = {
    GET_PROCESS_FORM_REQUEST: `${NAMESPACE}/get/request`,
    GET_PROCESS_FORM_RESPONSE: `${NAMESPACE}/get/response`,

    SUBMIT_PROCESS_FORM_REQUEST: `${NAMESPACE}/submit/request`,
    SUBMIT_PROCESS_FORM_RESPONSE: `${NAMESPACE}/submit/response`,

    START_PROCESS_WIZARD: `${NAMESPACE}/wizard/start`,
    STOP_PROCESS_WIZARD: `${NAMESPACE}/wizard/stop`,

    START_PROCESS_FORM: `${NAMESPACE}/form/start`,

    RESET_PROCESS_FORMS: `${NAMESPACE}/reset`
};

export const actions = {
    getProcessForm: (processInstanceId: ConcordId, formName: string): GetProcessFormRequest => ({
        type: actionTypes.GET_PROCESS_FORM_REQUEST,
        processInstanceId,
        formName
    }),

    submitProcessForm: (
        processInstanceId: ConcordId,
        formName: string,
        wizard: boolean,
        yieldFlow: boolean,
        data: FormDataType
    ): SubmitProcessFormRequest => ({
        type: actionTypes.SUBMIT_PROCESS_FORM_REQUEST,
        processInstanceId,
        formName,
        wizard,
        yieldFlow,
        data
    }),

    startWizard: (processInstanceId: ConcordId): StartProcessWizard => ({
        type: actionTypes.START_PROCESS_WIZARD,
        processInstanceId
    }),

    startForm: (processInstanceId: ConcordId, formName: string): StartProcessForm => ({
        type: actionTypes.START_PROCESS_FORM,
        processInstanceId,
        formName
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

function* onGetProcessForm({ processInstanceId, formName }: GetProcessFormRequest) {
    try {
        const response = yield call(apiGet, processInstanceId, formName);
        yield put({
            type: actionTypes.GET_PROCESS_FORM_RESPONSE,
            ...response
        });
    } catch (e) {
        yield handleErrors(actionTypes.GET_PROCESS_FORM_RESPONSE, e);
    }
}

function* onSubmitProcessForm({
    processInstanceId,
    formName,
    wizard,
    yieldFlow,
    data
}: SubmitProcessFormRequest) {
    try {
        const response = yield call(apiSubmit, processInstanceId, formName, data);
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
                    search: `fullScreen=true`
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
        let forms: FormListEntry[];

        while (true) {
            forms = yield call(apiList, processInstanceId);
            if (forms && forms.length > 0) {
                break;
            }

            const { status } = yield call(apiGetProcess, processInstanceId, []);

            const stopped = isFinal(status);

            if (stopped) {
                yield put(pushHistory(`/process/${processInstanceId}`));
                return;
            }

            yield call(delay, 1000);
        }

        const f = forms[0];
        yield startProcessForm(processInstanceId, f.name, f.custom, f.yield);
    } catch (e) {
        yield handleErrors(actionTypes.STOP_PROCESS_WIZARD, e);
    }
}

function* onStartProcessForm({ processInstanceId, formName }: StartProcessForm) {
    const form: FormInstanceEntry = yield call(apiGet, processInstanceId, formName);
    yield startProcessForm(form.processInstanceId, form.name, form.custom, form.yield);
}

function* startProcessForm(
    processInstanceId: ConcordId,
    formName: string,
    custom: boolean,
    yieldFlow: boolean
) {
    if (custom) {
        // a form with branding
        let { uri } = yield call(apiStartSession, processInstanceId, formName);

        // we can't proxy html resources using create-react-app
        // so we have to use another server to serve our custom forms
        // this is only for the development
        uri = updateForDev(uri);

        window.location.replace(uri);
    } else {
        // regular form
        const path = {
            pathname: `/process/${processInstanceId}/form/${formName}/wizard`,
            search: `fullScreen=true&yieldFlow=${yieldFlow}`
        };
        yield put(replaceHistory(path));
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.GET_PROCESS_FORM_REQUEST, onGetProcessForm),
        takeLatest(actionTypes.SUBMIT_PROCESS_FORM_REQUEST, onSubmitProcessForm),
        takeLatest(actionTypes.START_PROCESS_WIZARD, onStartProcessWizard),
        takeLatest(actionTypes.START_PROCESS_FORM, onStartProcessForm)
    ]);
};
