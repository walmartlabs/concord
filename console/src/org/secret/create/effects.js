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
import type { ConcordKey } from '../../../types';
import { combineReducers } from 'redux';
import { call, fork, put, takeLatest, all } from 'redux-saga/effects';
import { reset as resetForm } from 'redux-form';

import * as api from './api';

const NAMESPACE = 'org/secret';

const actionTypes = {
  CREATE_REQUEST: `${NAMESPACE}/create/request`,
  CREATE_RESPONSE: `${NAMESPACE}/create/response`,
  CREATE_RESET: `${NAMESPACE}/create/reset`
};

// Actions

export const actions = {
  createNewSecret: (orgName: ConcordKey, req: any) => ({
    type: actionTypes.CREATE_REQUEST,
    orgName,
    ...req
  }),

  reset: () => ({
    type: actionTypes.CREATE_RESET
  })
};

// Reducers

const response = (state = null, { type, ...rest }) => {
  switch (type) {
    case actionTypes.CREATE_REQUEST:
    case actionTypes.CREATE_RESET: {
      return null;
    }
    case actionTypes.CREATE_RESPONSE: {
      return rest;
    }
    default: {
      return state;
    }
  }
};

const loading = (state = false, { type }) => {
  switch (type) {
    case actionTypes.CREATE_REQUEST: {
      return true;
    }
    case actionTypes.CREATE_RESPONSE:
    case actionTypes.CREATE_RESET: {
      return false;
    }
    default: {
      return state;
    }
  }
};

const error = (state = null, { type, error, message }) => {
  switch (type) {
    case actionTypes.CREATE_RESPONSE: {
      if (!error) {
        return null;
      }
      return message;
    }
    case actionTypes.CREATE_RESET: {
      return null;
    }
    default: {
      return state;
    }
  }
};

export const reducers = combineReducers({ response, error, loading });

// Selectors

export const selectors = {
  response: (state: any) => state.response,
  error: (state: any) => state.error,
  loading: (state: any) => state.loading
};

// Sagas

function* onCreate(action: any): Generator<*, *, *> {
  try {
    const resp = yield call(api.create, action);
    if (resp.error) {
      yield put({
        type: actionTypes.CREATE_RESPONSE,
        error: true,
        message: resp.message || 'Error while creating a secret'
      });
    } else {
      yield put({
        type: actionTypes.CREATE_RESPONSE,
        ...resp
      });

      yield put(resetForm('newSecretForm'));
    }
  } catch (e) {
    yield put({
      type: actionTypes.CREATE_RESPONSE,
      error: true,
      message: e.message || 'Error while creating a secret'
    });
  }
}

function* onReset(action: any): Generator<*, *, *> {
  yield put(resetForm('newSecretForm'));
}

export function* sagas(): Generator<*, *, *> {
  yield all([
    fork(takeLatest, actionTypes.CREATE_REQUEST, onCreate),
    fork(takeLatest, actionTypes.CREATE_RESET, onReset)
  ]);
}
