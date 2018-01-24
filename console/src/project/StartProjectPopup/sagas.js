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
import * as api from './api';
import types from './actions';

function* startProject({ repositoryId }: any): Generator<*, *, *> {
  try {
    const response = yield call(api.startProject, repositoryId);
    if (response.error) {
      yield put({
        response,
        type: types.PROJECT_START_RESPONSE,
        error: true
      });
    } else {
      yield put({
        type: types.PROJECT_START_RESPONSE,
        response
      });
    }
  } catch (e) {
    yield put({
      response: e,
      type: types.PROJECT_START_RESPONSE,
      error: true
    });
  }
}

export default function*(): Generator<*, *, *> {
  yield all([fork(takeLatest, types.PROJECT_START_REQUEST, startProject)]);
}
