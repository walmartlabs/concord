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
import { Action, combineReducers, Reducer } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';

import { ConcordKey } from '../../../api/common';
import {
    create as apiCreate,
    deleteSecret as apiDelete,
    get as apiGet,
    list as apiList,
    NewSecretEntry
} from '../../../api/org/secret';
import {
    genericResult,
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer,
    nullReducer
} from '../common';
import {
    CreateSecretRequest,
    CreateSecretState,
    DeleteSecretRequest,
    DeleteSecretState,
    GetSecretRequest,
    ListSecretsRequest,
    ListSecretsState,
    SecretDataResponse,
    Secrets,
    State
} from './types';

export { State };

const NAMESPACE = 'secrets';

const actionTypes = {
    GET_SECRET_REQUEST: `${NAMESPACE}/get/request`,
    LIST_SECRETS_REQUEST: `${NAMESPACE}/list/request`,
    SECRET_DATA_RESPONSE: `${NAMESPACE}/data/response`,

    CREATE_SECRET_REQUEST: `${NAMESPACE}/create/request`,
    CREATE_SECRET_RESPONSE: `${NAMESPACE}/create/response`,

    DELETE_SECRET_REQUEST: `${NAMESPACE}/delete/request`,
    DELETE_SECRET_RESPONSE: `${NAMESPACE}/delete/response`,

    RESET_SECRET: `${NAMESPACE}/reset`
};

export const actions = {
    getSecret: (orgName: ConcordKey, secretName: ConcordKey): GetSecretRequest => ({
        type: actionTypes.GET_SECRET_REQUEST,
        orgName,
        secretName
    }),

    listSecrets: (orgName: ConcordKey): ListSecretsRequest => ({
        type: actionTypes.LIST_SECRETS_REQUEST,
        orgName
    }),

    createSecret: (orgName: ConcordKey, entry: NewSecretEntry): CreateSecretRequest => ({
        type: actionTypes.CREATE_SECRET_REQUEST,
        orgName,
        entry
    }),

    deleteSecret: (orgName: ConcordKey, secretName: ConcordKey): DeleteSecretRequest => ({
        type: actionTypes.DELETE_SECRET_REQUEST,
        orgName,
        secretName
    }),

    reset: (): Action => ({
        type: actionTypes.RESET_SECRET
    })
};

const secretById: Reducer<Secrets> = (state = {}, { type, error, items }: SecretDataResponse) => {
    switch (type) {
        case actionTypes.LIST_SECRETS_REQUEST:
            return {};
        case actionTypes.SECRET_DATA_RESPONSE:
            if (error || !items) {
                return {};
            }

            const result = {};
            items.forEach((o) => {
                result[o.id] = o;
            });
            return result;
        default:
            return state;
    }
};

const listSecretsReducer = combineReducers<ListSecretsState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_SECRETS_REQUEST],
        [actionTypes.SECRET_DATA_RESPONSE]
    ),
    error: makeErrorReducer([actionTypes.LIST_SECRETS_REQUEST], [actionTypes.SECRET_DATA_RESPONSE]),
    response: nullReducer()
});

const createSecretReducer = combineReducers<CreateSecretState>({
    running: makeLoadingReducer(
        [actionTypes.CREATE_SECRET_REQUEST],
        [actionTypes.RESET_SECRET, actionTypes.CREATE_SECRET_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_SECRET, actionTypes.CREATE_SECRET_REQUEST],
        [actionTypes.CREATE_SECRET_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.CREATE_SECRET_RESPONSE, actionTypes.RESET_SECRET)
});

const deleteSecretReducers = combineReducers<DeleteSecretState>({
    running: makeLoadingReducer(
        [actionTypes.DELETE_SECRET_REQUEST],
        [actionTypes.DELETE_SECRET_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.DELETE_SECRET_REQUEST],
        [actionTypes.DELETE_SECRET_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.DELETE_SECRET_RESPONSE, actionTypes.RESET_SECRET)
});

export const reducers = combineReducers<State>({
    secretById, // TODO use makeEntityByIdReducer

    listSecrets: listSecretsReducer,
    createSecret: createSecretReducer,
    deleteSecret: deleteSecretReducers
});

export const selectors = {
    secretByName: (state: State, orgName: ConcordKey, secretName: ConcordKey) => {
        for (const id of Object.keys(state.secretById)) {
            const p = state.secretById[id];
            if (p.orgName === orgName && p.name === secretName) {
                return p;
            }
        }

        return;
    }
};

function* onGet({ orgName, secretName }: GetSecretRequest) {
    try {
        const response = yield call(apiGet, orgName, secretName);
        yield put({
            type: actionTypes.SECRET_DATA_RESPONSE,
            items: [response] // normalizing the data
        });
    } catch (e) {
        yield handleErrors(actionTypes.SECRET_DATA_RESPONSE, e);
    }
}

function* onList({ orgName }: ListSecretsRequest) {
    try {
        const response = yield call(apiList, orgName);
        yield put({
            type: actionTypes.SECRET_DATA_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.SECRET_DATA_RESPONSE, e);
    }
}

function* onCreate({ orgName, entry }: CreateSecretRequest) {
    try {
        const response = yield call(apiCreate, orgName, entry);
        yield put({
            type: actionTypes.CREATE_SECRET_RESPONSE,
            orgName,
            name: entry.name,
            password: response.password,
            publicKey: response.publicKey
        });
    } catch (e) {
        yield handleErrors(actionTypes.CREATE_SECRET_RESPONSE, e);
    }
}

function* onDelete({ orgName, secretName }: DeleteSecretRequest) {
    try {
        const response = yield call(apiDelete, orgName, secretName);
        yield put(genericResult(actionTypes.DELETE_SECRET_RESPONSE, response));
    } catch (e) {
        yield handleErrors(actionTypes.DELETE_SECRET_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.LIST_SECRETS_REQUEST, onList),
        takeLatest(actionTypes.GET_SECRET_REQUEST, onGet),
        takeLatest(actionTypes.CREATE_SECRET_REQUEST, onCreate),
        takeLatest(actionTypes.DELETE_SECRET_REQUEST, onDelete)
    ]);
};
