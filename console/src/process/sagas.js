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
import types from './actions';
import * as api from './api';

function* load(action: any): Generator<*, *, *> {
  const { instanceId } = action;

  try {
    const [status, forms] = yield [
      call(api.fetchStatus, instanceId),
      call(api.listForms, instanceId)
    ];

    yield put({
      type: types.PROCESS_INFO_RESPONSE,
      instanceId,
      response: {
        ...status,
        forms
      }
    });
  } catch (e) {
    yield put({
      type: types.PROCESS_INFO_RESPONSE,
      instanceId,
      error: true,
      message: e.message || 'Error while loading a process info'
    });
  }
}

function* kill(action: any): Generator<*, *, *> {
  const { instanceId, onSuccess } = action;

  try {
    yield call(api.kill, instanceId);

    yield put({
      type: types.PROCESS_KILL_RESPONSE,
      instanceId
    });

    if (onSuccess) {
      for (const a of onSuccess) {
        yield put(a);
      }
    }
  } catch (e) {
    yield put({
      type: types.PROCESS_KILL_RESPONSE,
      instanceId,
      error: true,
      message: e.message || 'Error while killing a process'
    });
  }
}

export default function*(): Generator<*, *, *> {
  yield all([
    fork(takeLatest, types.PROCESS_INFO_REQUEST, load),
    fork(takeLatest, types.PROCESS_KILL_REQUEST, kill)
  ]);
}
