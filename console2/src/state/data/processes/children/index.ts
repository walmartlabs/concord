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
import { ConcordId } from '../../../../api/common';
import { ListProcessChildrenState, ListProcessChildrenRequest, State } from './types';
import { combineReducers } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';
import { list as apiProcessList, SearchFilter } from '../../../../api/org/process';
import {
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer
} from '../../common';

const NAMESPACE = 'processes/children';

const actionTypes = {
    LIST_PROCESS_CHILDREN_REQUEST: `${NAMESPACE}/children/list/request`,
    LIST_PROCESS_CHILDREN_RESPONSE: `${NAMESPACE}/children/list/response`,
    RESET_PROCESS_LIST: `${NAMESPACE}/reset`
};

export const actions = {
    listChildren: (parentId: ConcordId, filters?: SearchFilter): ListProcessChildrenRequest => ({
        type: actionTypes.LIST_PROCESS_CHILDREN_REQUEST,
        parentId,
        filters
    }),

    reset: () => ({
        type: actionTypes.RESET_PROCESS_LIST
    })
};

const listReducers = combineReducers<ListProcessChildrenState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_PROCESS_CHILDREN_REQUEST],
        [actionTypes.LIST_PROCESS_CHILDREN_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_PROCESS_LIST, actionTypes.LIST_PROCESS_CHILDREN_REQUEST],
        [actionTypes.LIST_PROCESS_CHILDREN_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.LIST_PROCESS_CHILDREN_RESPONSE)
});

export const reducers = combineReducers<State>({
    listChildren: listReducers
});

export const selectors = {
    processChildren: (state: State) => {
        return state.listChildren.response ? state.listChildren.response.items : [];
    }
};

function* onList({ parentId, filters }: ListProcessChildrenRequest) {
    try {
        const orgName = undefined;
        const projectName = undefined;
        if (filters === undefined) {
            filters = { parentInstanceId: parentId };
        } else {
            filters.parentInstanceId = parentId;
        }
        const response = yield call(apiProcessList, orgName, projectName, filters);
        yield put({
            type: actionTypes.LIST_PROCESS_CHILDREN_RESPONSE,
            items: response.processes
        });
    } catch (e) {
        yield handleErrors(actionTypes.LIST_PROCESS_CHILDREN_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.LIST_PROCESS_CHILDREN_REQUEST, onList)]);
};
