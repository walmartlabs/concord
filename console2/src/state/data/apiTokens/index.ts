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

import { ConcordId } from '../../../api/common';
import {
    create as apiCreate,
    deleteToken as apiDelete,
    list as apiList,
    NewTokenEntry
} from '../../../api/profile/api_token';
import {
    genericResult,
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer,
    nullReducer
} from '../common';
import {
    CreateTokenRequest,
    CreateTokenState,
    DeleteTokenRequest,
    DeleteTokenState,
    ListTokensState,
    TokenDataResponse,
    Tokens,
    State
} from './types';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'apiTokens';

const actionTypes = {
    LIST_TOKENS_REQUEST: `${NAMESPACE}/list/request`,
    TOKEN_DATA_RESPONSE: `${NAMESPACE}/data/response`,

    CREATE_TOKEN_REQUEST: `${NAMESPACE}/create/request`,
    CREATE_TOKEN_RESPONSE: `${NAMESPACE}/create/response`,

    DELETE_TOKEN_REQUEST: `${NAMESPACE}/delete/request`,
    DELETE_TOKEN_RESPONSE: `${NAMESPACE}/delete/response`,

    RESET_TOKEN: `${NAMESPACE}/reset`
};

export const actions = {
    listTokens: (): Action => ({
        type: actionTypes.LIST_TOKENS_REQUEST
    }),

    createToken: (entry: NewTokenEntry): CreateTokenRequest => ({
        type: actionTypes.CREATE_TOKEN_REQUEST,
        entry
    }),

    deleteToken: (id: ConcordId): DeleteTokenRequest => ({
        type: actionTypes.DELETE_TOKEN_REQUEST,
        id
    }),

    reset: (): Action => ({
        type: actionTypes.RESET_TOKEN
    })
};

const tokenById: Reducer<Tokens> = (state = {}, { type, error, items }: TokenDataResponse) => {
    switch (type) {
        case actionTypes.LIST_TOKENS_REQUEST:
            return {};
        case actionTypes.TOKEN_DATA_RESPONSE:
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

const listTokensReducer = combineReducers<ListTokensState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_TOKENS_REQUEST],
        [actionTypes.TOKEN_DATA_RESPONSE]
    ),
    error: makeErrorReducer([actionTypes.LIST_TOKENS_REQUEST], [actionTypes.TOKEN_DATA_RESPONSE]),
    response: nullReducer()
});

const createTokenReducer = combineReducers<CreateTokenState>({
    running: makeLoadingReducer(
        [actionTypes.CREATE_TOKEN_REQUEST],
        [actionTypes.RESET_TOKEN, actionTypes.CREATE_TOKEN_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_TOKEN, actionTypes.CREATE_TOKEN_REQUEST],
        [actionTypes.CREATE_TOKEN_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.CREATE_TOKEN_RESPONSE, actionTypes.RESET_TOKEN)
});

const deleteTokenReducers = combineReducers<DeleteTokenState>({
    running: makeLoadingReducer(
        [actionTypes.DELETE_TOKEN_REQUEST],
        [actionTypes.DELETE_TOKEN_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.DELETE_TOKEN_REQUEST],
        [actionTypes.DELETE_TOKEN_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.DELETE_TOKEN_RESPONSE, actionTypes.RESET_TOKEN)
});

const loading = makeLoadingReducer(
    [actionTypes.LIST_TOKENS_REQUEST, actionTypes.CREATE_TOKEN_REQUEST],
    [actionTypes.TOKEN_DATA_RESPONSE]
);

const errorMsg = makeErrorReducer(
    [actionTypes.LIST_TOKENS_REQUEST, actionTypes.CREATE_TOKEN_REQUEST],
    [actionTypes.TOKEN_DATA_RESPONSE]
);

export const reducers = combineReducers<State>({
    tokenById,
    loading,
    error: errorMsg,

    listTokens: listTokensReducer,
    createToken: createTokenReducer,
    deleteToken: deleteTokenReducers
});

function* onList() {
    try {
        const response = yield call(apiList);
        yield put({
            type: actionTypes.TOKEN_DATA_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.TOKEN_DATA_RESPONSE, e);
    }
}

function* onCreate({ entry }: CreateTokenRequest) {
    try {
        const response = yield call(apiCreate, entry);
        yield put({
            type: actionTypes.CREATE_TOKEN_RESPONSE,
            id: response.id,
            key: response.key
        });
    } catch (e) {
        yield handleErrors(actionTypes.CREATE_TOKEN_RESPONSE, e);
    }
}

function* onDelete({ id }: DeleteTokenRequest) {
    try {
        const response = yield call(apiDelete, id);
        yield put(genericResult(actionTypes.DELETE_TOKEN_RESPONSE, response));
    } catch (e) {
        yield handleErrors(actionTypes.DELETE_TOKEN_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.LIST_TOKENS_REQUEST, onList),
        takeLatest(actionTypes.CREATE_TOKEN_REQUEST, onCreate),
        takeLatest(actionTypes.DELETE_TOKEN_REQUEST, onDelete)
    ]);
};
