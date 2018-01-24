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
import { all, call, fork, put, takeLatest } from 'redux-saga/effects';
import * as api from './api';
import types from './actions';

function* refreshRepo(action: any): Generator<*, *, *> {
  try {
    const { orgName, projectName, repositoryName } = action;

    const response = yield call(api.refreshRepository, orgName, projectName, repositoryName);
    if (response.error) {
      yield put({
        type: types.REPOSITORY_REFRESH_RESPONSE,
        error: true,
        message: response.message || 'Error while refreshing a repository'
      });
    } else {
      yield put({
        type: types.REPOSITORY_REFRESH_RESPONSE,
        response
      });
    }
  } catch (e) {
    yield put({
      type: types.REPOSITORY_REFRESH_RESPONSE,
      error: true,
      message: e.message || 'Error while refreshing a repository'
    });
  }
}

export default function*(): Generator<*, *, *> {
  yield all([fork(takeLatest, types.REPOSITORY_REFRESH_REQUEST, refreshRepo)]);
}
