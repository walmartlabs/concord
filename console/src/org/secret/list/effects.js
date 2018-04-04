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
import { combineReducers } from 'redux';
import { call, fork, put, takeLatest, all } from 'redux-saga/effects';

import type { ConcordKey } from '../../../types';
import * as common from '../../../reducers/common';

import * as api from './api';

const NAMESPACE = 'user/secret';

export const actionTypes = {
    USER_SECRET_LIST_REQUEST: `${NAMESPACE}/list/request`,
    USER_SECRET_LIST_RESPONSE: `${NAMESPACE}/list/response`,

    USER_SECRET_DELETE_REQUEST: `${NAMESPACE}/delete/request`,
    USER_SECRET_DELETE_RESPONSE: `${NAMESPACE}/delete/response`,

    USER_SECRET_PUBLICKEY_REQUEST: `${NAMESPACE}/publickey/request`,
    USER_SECRET_PUBLICKEY_RESPONSE: `${NAMESPACE}/publickey/response`,

    USER_SECRET_STORE_TYPE_REQUEST: `${NAMESPACE}/storetype/request`,
    USER_SECRET_STORE_TYPE_RESPONSE: `${NAMESPACE}/storetype/response`
};

// Actions

export const actions = {
    fetchSecretList: (orgName: ConcordKey) => ({
        type: actionTypes.USER_SECRET_LIST_REQUEST,
        orgName
    }),

    deleteSecret: (orgName: ConcordKey, name: ConcordKey, onSuccess: Array<string>) => ({
        type: actionTypes.USER_SECRET_DELETE_REQUEST,
        orgName,
        name,
        onSuccess
    }),

    getSecretStoreTypeList: () => ({
        type: actionTypes.USER_SECRET_STORE_TYPE_REQUEST
    })
};

// Reducers

const rows = (state = [], { type, error, response }) => {
    switch (type) {
        case actionTypes.USER_SECRET_LIST_RESPONSE: {
            if (error) {
                return state;
            }
            return response;
        }
        default: {
            return state;
        }
    }
};

const secretStoreTypelist = (state = [], { type, error, response }) => {
    switch (type) {
        case actionTypes.USER_SECRET_STORE_TYPE_RESPONSE: {
            if (error) {
                return state;
            }
            return response;
        }
        default: {
            return state;
        }
    }
};

const loading = common.booleanTrigger(
    actionTypes.USER_SECRET_LIST_REQUEST,
    actionTypes.USER_SECRET_LIST_RESPONSE
);
const error = common.error(actionTypes.USER_SECRET_LIST_RESPONSE);

const deleteError = (state = null, action) => {
    switch (action.type) {
        case actionTypes.USER_SECRET_LIST_REQUEST:
        case actionTypes.USER_SECRET_LIST_RESPONSE: {
            return null;
        }
        case actionTypes.USER_SECRET_DELETE_RESPONSE: {
            if (!action.error) {
                return null;
            }
            return action.message;
        }
        default: {
            return state;
        }
    }
};

const inFlight = (state = [], action) => {
    switch (action.type) {
        case actionTypes.USER_SECRET_DELETE_REQUEST: {
            const v = `${action.orgName}/${action.name}`;
            return [...state, v];
        }
        case actionTypes.USER_SECRET_DELETE_RESPONSE: {
            const x = `${action.orgName}/${action.name}`;
            return state.filter((y) => y !== x);
        }
        default: {
            return state;
        }
    }
};

const publicKey = (state = null, action) => {
    switch (action.type) {
        case actionTypes.USER_SECRET_PUBLICKEY_REQUEST: {
            return null;
        }
        case actionTypes.USER_SECRET_PUBLICKEY_RESPONSE: {
            return action.publicKey || null;
        }
        default: {
            return state;
        }
    }
};

const publicKeyError = (state = null, action) => {
    switch (action.type) {
        case actionTypes.USER_SECRET_PUBLICKEY_REQUEST:
            return null;
        case actionTypes.USER_SECRET_PUBLICKEY_RESPONSE:
            return action.error || null;
        default:
            return state;
    }
};

const secretStoreTypelistError = (state = null, action) => {
    switch (action.type) {
        case actionTypes.USER_SECRET_STORE_TYPE_REQUEST:
            return null;
        case actionTypes.USER_SECRET_STORE_TYPE_RESPONSE:
            return action.error || null;
        default:
            return state;
    }
};

export const reducers = combineReducers({
    rows,
    loading,
    error,
    inFlight,
    deleteError,
    publicKey,
    publicKeyError,
    secretStoreTypelist,
    secretStoreTypelistError
});

// Selectors

export const selectors = {
    getRows: (state: any) => state.rows,
    getIsLoading: (state: any) => state.loading,
    getError: (state: any) => state.error,
    getDeleteError: (state: any) => state.deleteError,
    isInFlight: (state: any, orgName: any, name: any) =>
        state.inFlight.includes(`${orgName}/${name}`),
    getPublicKeyError: (state: any) => state.publicKeyError,
    getPublicKey: (state: any) => state.publicKey,
    getSecretStoreTypeListError: (state: any) => state.secretStoreTypelistError,
    getSecretStoreTypeList: (state: any) => state.secretStoreTypelist
};

// Sagas

function* fetchSecretList(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.fetchSecretList, action.orgName);
        yield put({
            type: actionTypes.USER_SECRET_LIST_RESPONSE,
            response
        });
    } catch (e) {
        yield put({
            type: actionTypes.USER_SECRET_LIST_RESPONSE,
            error: true,
            message: e.message || 'Error while loading data'
        });
    }
}

function* deleteSecret(action: any): Generator<*, *, *> {
    try {
        yield call(api.deleteSecret, action.orgName, action.name);
        yield put({
            type: actionTypes.USER_SECRET_DELETE_RESPONSE,
            orgName: action.orgName,
            name: action.name
        });

        if (action.onSuccess) {
            for (const a of action.onSuccess) {
                yield put(a);
            }
        }
    } catch (e) {
        console.error('deleteSecret -> error', e);
        yield put({
            type: actionTypes.USER_SECRET_DELETE_RESPONSE,
            orgName: action.orgName,
            name: action.name,
            error: true,
            message: e.message || 'Error while removing a secret'
        });
    }
}

function* getPublicKey(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.getPublicKey, action.orgName, action.name);

        yield put({
            type: actionTypes.USER_SECRET_PUBLICKEY_RESPONSE,
            publicKey: response.publicKey
        });
    } catch (e) {
        yield put({
            type: actionTypes.USER_SECRET_PUBLICKEY_RESPONSE,
            error: `Error during retrieval: ${e.message}`
        });
    }
}

function* getSecretStoreTypeList(action: any): Generator<*, *, *> {
    try {
        const response = yield call(api.getSecretStoreTypeList);
        yield put({
            type: actionTypes.USER_SECRET_STORE_TYPE_RESPONSE,
            response
        });
    } catch (e) {
        yield put({
            type: actionTypes.USER_SECRET_STORE_TYPE_RESPONSE,
            error: true,
            message: e.message || 'Error while loading store type data'
        });
    }
}

export const sagas = function*(): Generator<*, *, *> {
    yield all([
        fork(takeLatest, actionTypes.USER_SECRET_LIST_REQUEST, fetchSecretList),
        fork(takeLatest, actionTypes.USER_SECRET_DELETE_REQUEST, deleteSecret),
        fork(takeLatest, actionTypes.USER_SECRET_PUBLICKEY_REQUEST, getPublicKey),
        fork(takeLatest, actionTypes.USER_SECRET_STORE_TYPE_REQUEST, getSecretStoreTypeList)
    ]);
};
