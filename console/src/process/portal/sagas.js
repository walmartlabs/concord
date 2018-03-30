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
import { call, fork, put, takeLatest, all } from 'redux-saga/effects';
import { push as pushHistory } from 'react-router-redux';
import types from './actions';
import * as api from '../api';

function* startProcess(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.start, action.entryPoint);
        yield put({
            type: types.PROCESS_PORTAL_START_RESPONSE,
            response
        });

        const path = {
            pathname: `/process/${response.instanceId}/wizard`,
            query: { fullScreen: true }
        };
        yield put(pushHistory(path));
    } catch (e) {
        yield put({
            type: types.PROCESS_PORTAL_START_RESPONSE,
            error: true,
            message: e.message || 'Error while starting a process'
        });
    }
}

export default function*(): Generator<*, *, *> {
    yield all([fork(takeLatest, types.PROCESS_PORTAL_START_REQUEST, startProcess)]);
}
