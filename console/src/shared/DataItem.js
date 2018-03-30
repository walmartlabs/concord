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

const NAMESPACE = 'shared/data_item';

const actionTypes = {
    DATA_ITEM_REQUEST: `${NAMESPACE}/request`,
    DATA_ITEM_RESPONSE: `${NAMESPACE}/response`,
    DATA_ITEM_RESET: `${NAMESPACE}/reset`,

    DATA_ITEM_SAVE_REQUEST: `${NAMESPACE}/save/request`,
    DATA_ITEM_SAVE_RESPONSE: `${NAMESPACE}/save/response`,

    DATA_ITEM_DELETE_REQUEST: `${NAMESPACE}/delete/request`,
    DATA_ITEM_DELETE_RESPONSE: `${NAMESPACE}/delete/response`
};

/**
 * Generates actions, reducers, selectors and sagas for a simple piece of data backed by a function.
 * Includes a loading state indicator and error handling.
 *
 * @param key Unique ID of the component. Will be used to dispatch and process actions.
 * @param initial Initial state.
 * @param loadFn
 * @param saveFn
 * @param deleteFn
 */
export default (
    key: string,
    initial: any,
    loadFn: Function,
    saveFn: Function,
    deleteFn: Function
) => {
    // ACTIONS

    const types = {
        DATA_ITEM_REQUEST: `${actionTypes.DATA_ITEM_REQUEST}/${key}`,
        DATA_ITEM_RESPONSE: `${actionTypes.DATA_ITEM_RESPONSE}/${key}`,
        DATA_ITEM_RESET: `${actionTypes.DATA_ITEM_RESET}/${key}`,

        DATA_ITEM_SAVE_REQUEST: `${actionTypes.DATA_ITEM_SAVE_REQUEST}/${key}`,
        DATA_ITEM_SAVE_RESPONSE: `${actionTypes.DATA_ITEM_SAVE_RESPONSE}/${key}`,

        DATA_ITEM_DELETE_REQUEST: `${actionTypes.DATA_ITEM_DELETE_REQUEST}/${key}`,
        DATA_ITEM_DELETE_RESPONSE: `${actionTypes.DATA_ITEM_DELETE_RESPONSE}/${key}`
    };

    const actions = {
        loadData: (args: ?any = []) => ({
            type: types.DATA_ITEM_REQUEST,
            componentKey: key,
            args
        }),

        saveData: (data: any, onSuccess: [any]) => ({
            type: types.DATA_ITEM_SAVE_REQUEST,
            componentKey: key,
            data,
            onSuccess
        }),

        resetData: () => ({
            type: types.DATA_ITEM_RESET,
            componentKey: key,
            data: initial
        }),

        deleteData: (args: ?any = [], onSuccess: [any]) => ({
            type: types.DATA_ITEM_DELETE_REQUEST,
            componentKey: key,
            args,
            onSuccess
        })
    };

    // REDUCERS

    const data = (state = initial, { type, error, response, data }) => {
        switch (type) {
            case types.DATA_ITEM_RESPONSE:
                if (error) {
                    return state;
                }
                return response;
            case types.DATA_ITEM_RESET:
                return data;
            default:
                return state;
        }
    };

    const error = (state = null, { type, error, message }) => {
        switch (type) {
            case types.DATA_ITEM_RESPONSE:
            case types.DATA_ITEM_SAVE_RESPONSE:
            case types.DATA_ITEM_DELETE_RESPONSE:
                if (error) {
                    return message;
                }
                return null;
            default:
                return state;
        }
    };

    const loading = (state = false, { type }) => {
        switch (type) {
            case types.DATA_ITEM_REQUEST:
            case types.DATA_ITEM_SAVE_REQUEST:
            case types.DATA_ITEM_DELETE_REQUEST:
                return true;
            case types.DATA_ITEM_RESPONSE:
            case types.DATA_ITEM_SAVE_RESPONSE:
            case types.DATA_ITEM_DELETE_RESPONSE:
                return false;
            default:
                return state;
        }
    };

    const reducers = combineReducers({ data, error, loading });

    // SELECTORS

    const selectors = {
        getData: (state: any) => state.data,
        getError: (state: any) => state.error,
        isLoading: (state: any) => state.loading
    };

    // SAGAS

    function* loadData({ args }: any): Generator<*, *, *> {
        try {
            const response = yield call(loadFn, ...args);
            yield put({
                type: types.DATA_ITEM_RESPONSE,
                response
            });
        } catch (e) {
            yield put({
                type: types.DATA_ITEM_RESPONSE,
                error: true,
                message: e.message || 'Error while loading data'
            });
        }
    }

    function* saveData({ data, onSuccess }: any): Generator<*, *, *> {
        try {
            const response = yield call(saveFn, data);

            yield put({
                type: types.DATA_ITEM_SAVE_RESPONSE,
                response
            });

            if (onSuccess) {
                for (const a of onSuccess) {
                    yield put(a);
                }
            }
        } catch (e) {
            yield put({
                type: types.DATA_ITEM_SAVE_RESPONSE,
                error: true,
                message: e.message || 'Error while saving data'
            });
        }
    }

    function* deleteData({ args, onSuccess }: any): Generator<*, *, *> {
        try {
            const response = yield call(deleteFn, ...args);

            yield put({
                type: types.DATA_ITEM_DELETE_RESPONSE,
                response
            });

            if (onSuccess) {
                for (const a of onSuccess) {
                    yield put(a);
                }
            }
        } catch (e) {
            yield put({
                type: types.DATA_ITEM_DELETE_RESPONSE,
                error: true,
                message: e.message || 'Error while removing data'
            });
        }
    }

    function* sagas(): Generator<*, *, *> {
        yield all([
            fork(takeLatest, types.DATA_ITEM_REQUEST, loadData),
            fork(takeLatest, types.DATA_ITEM_SAVE_REQUEST, saveData),
            fork(takeLatest, types.DATA_ITEM_DELETE_REQUEST, deleteData)
        ]);
    }

    return { types, actions, reducers, selectors, sagas };
};
