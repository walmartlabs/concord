/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import { combineReducers, Reducer } from 'redux';
import { call, put, takeLatest } from 'redux-saga/effects';
import { list as apiList } from '../../../api/org';
import { handleErrors, makeErrorReducer, makeLoadingReducer } from '../common';
import { ListOrganizationsRequest, ListOrganizationsResponse, Organizations, State } from './types';

export { State };

const NAMESPACE = 'orgs';

const actionTypes = {
    LIST_ORGANIZATIONS_REQUEST: `${NAMESPACE}/list/request`,
    LIST_ORGANIZATIONS_RESPONSE: `${NAMESPACE}/list/response`
};

export const actions = {
    listOrgs: (onlyCurrent: boolean): ListOrganizationsRequest => ({
        type: actionTypes.LIST_ORGANIZATIONS_REQUEST,
        onlyCurrent
    })
};

const orgById: Reducer<Organizations> = (
    state = {},
    { type, error, items }: ListOrganizationsResponse
) => {
    switch (type) {
        case actionTypes.LIST_ORGANIZATIONS_RESPONSE:
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

const loading = makeLoadingReducer(
    [actionTypes.LIST_ORGANIZATIONS_REQUEST],
    [actionTypes.LIST_ORGANIZATIONS_RESPONSE]
);

const listError = makeErrorReducer(
    [actionTypes.LIST_ORGANIZATIONS_REQUEST],
    [actionTypes.LIST_ORGANIZATIONS_RESPONSE]
);

export const reducers = combineReducers<State>({
    orgById, // TODO use makeEntityByIdReducer
    loading,
    error: listError
});

function* onList({ onlyCurrent }: ListOrganizationsRequest) {
    try {
        const response = yield call(apiList, onlyCurrent);
        yield put({
            type: actionTypes.LIST_ORGANIZATIONS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.LIST_ORGANIZATIONS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield takeLatest(actionTypes.LIST_ORGANIZATIONS_REQUEST, onList);
};
