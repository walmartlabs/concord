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
import { fork, call, put, takeLatest, select, all } from 'redux-saga/effects';
import { push as pushHistory } from 'react-router-redux';
import types from './actions';
import { actions as session } from '../session';
import { selectors } from '../session';
import * as api from './api';

function* doLogin(action: any): Generator<*, *, *> {
  try {
    const response = yield call(api.login, action.username, action.password);

    yield put({
      type: types.LOGIN_RESPONSE,
      ...response
    });

    const destination = yield select(({ session }) => selectors.getDestination(session));

    yield put(session.setCurrent(response));

    if (destination) {
      // redirect to a previosly-saved destination
      yield put(pushHistory(destination));
    } else {
      // redirect to the default path
      yield put(pushHistory('/'));
    }
  } catch (e) {
    let msg = e.message || 'Log in error';
    if (e.status === 401) {
      msg = 'Invalid username and/or password';
    }

    yield put({
      type: types.LOGIN_RESPONSE,
      error: true,
      message: msg
    });
  }
}

function* doRefresh(action: any): Generator<*, *, *> {
  try {
    const response = yield call(api.login);
    yield put(session.setCurrent(response));
  } catch (e) {
    yield put(pushHistory('/login'));
  }
}

export default function*(): Generator<*, *, *> {
  yield all([
    fork(takeLatest, types.LOGIN_REQUEST, doLogin),
    fork(takeLatest, types.LOGIN_REFRESH, doRefresh)
  ]);
}
