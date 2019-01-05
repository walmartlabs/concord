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
import { combineReducers } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';
import { ConcordKey } from '../../../api/common';
import { listTriggers as apiList } from '../../../api/org/project/repository';
import { handleErrors, makeErrorReducer, makeLoadingReducer, makeResponseReducer } from '../common';
import { ListTriggersRequest, ListTriggersState, State } from './types';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'triggers';

const actionTypes = {
    LIST_TRIGGERS_REQUEST: `${NAMESPACE}/list/request`,
    LIST_TRIGGERS_RESPONSE: `${NAMESPACE}/list/response`
};

export const actions = {
    listTriggers: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        repoName: ConcordKey
    ): ListTriggersRequest => ({
        type: actionTypes.LIST_TRIGGERS_REQUEST,
        orgName,
        projectName,
        repoName
    })
};

const listTriggersReducer = combineReducers<ListTriggersState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_TRIGGERS_REQUEST],
        [actionTypes.LIST_TRIGGERS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.LIST_TRIGGERS_REQUEST],
        [actionTypes.LIST_TRIGGERS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.LIST_TRIGGERS_RESPONSE)
});

export const reducers = combineReducers<State>({
    listTriggers: listTriggersReducer
});

function* onList({ orgName, projectName, repoName }: ListTriggersRequest) {
    try {
        const response = yield call(apiList, orgName, projectName, repoName);
        yield put({
            type: actionTypes.LIST_TRIGGERS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.LIST_TRIGGERS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.LIST_TRIGGERS_REQUEST, onList)]);
};
