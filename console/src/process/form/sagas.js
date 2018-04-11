/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
// @flow
import { delay } from 'redux-saga';
import { call, fork, put, takeLatest, all } from 'redux-saga/effects';
import { push as pushHistory } from 'react-router-redux';
import types from './actions';
import * as api from './api';

function* loadForm(action: any): Generator<*, *, *> {
    const { instanceId, formInstanceId } = action;
    try {
        const response = yield call(api.fetchForm, instanceId, formInstanceId);
        const { customForm } = response;
        if (customForm) {
            let { uri } = yield call(api.startSession, instanceId, formInstanceId);
            if (process.env.NODE_ENV !== 'production') {
                uri = 'http://localhost:8080' + uri;
            }
            window.location.replace(uri);
        }

        yield put({
            type: types.PROCESS_FORM_RESPONSE,
            instanceId,
            formInstanceId,
            response
        });
    } catch (e) {
        yield put({
            type: types.PROCESS_FORM_RESPONSE,
            instanceId,
            formInstanceId,
            error: true,
            message: e.message || 'Error while loading a process form'
        });
    }
}

function* submitForm(action: any): Generator<*, *, *> {
    const { instanceId, formInstanceId, data } = action;
    try {
        const response = yield call(api.submitForm, instanceId, formInstanceId, data);
        yield put({
            type: types.PROCESS_FORM_SUBMIT_RESPONSE,
            instanceId,
            formInstanceId,
            response
        });

        if (response.ok && action.wizard) {
            let path;
            if (action.yieldFlow) {
                yield delay(1000);
                path = {
                    pathname: `/process/${instanceId}`,
                    query: { fullScreen: false }
                };
            } else {
                path = {
                    pathname: `/process/${instanceId}/wizard`,
                    query: { fullScreen: true }
                };
            }
            yield put(pushHistory(path));
        }
    } catch (e) {
        yield put({
            type: types.PROCESS_FORM_SUBMIT_RESPONSE,
            instanceId,
            formInstanceId,
            error: true,
            message: e.message || 'Error while submitting a process form'
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield all([
        fork(takeLatest, types.PROCESS_FORM_REQUEST, loadForm),
        fork(takeLatest, types.PROCESS_FORM_SUBMIT_REQUEST, submitForm)
    ]);
}
