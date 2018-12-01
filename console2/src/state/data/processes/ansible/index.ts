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
import {
    AnsibleHostsState,
    AnsibleStatsState,
    GetAnsibleStatsRequest,
    ListAnsibleHostsRequest,
    State
} from './types';
import { combineReducers } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';
import {
    AnsibleHost,
    AnsibleStatsEntry,
    listAnsibleHosts as apiListAnsibleHosts,
    getAnsibleStats as apiGetAnsibleStats,
    SearchFilter
} from '../../../../api/process/ansible';
import {
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer
} from '../../common';
import { ConcordId } from '../../../../api/common';

const NAMESPACE = 'processes/ansible';
const ANSIBLE_HOST_LIMIT = 10;

export const actionTypes = {
    GET_ANSIBLE_STATS_REQUEST: `${NAMESPACE}/stats/request`,
    GET_ANSIBLE_STATS_RESPONSE: `${NAMESPACE}/stats/response`,

    GET_ANSIBLE_STATS_RESET: `${NAMESPACE}/reset`,

    LIST_ANSIBLE_HOSTS_REQUEST: `${NAMESPACE}/hosts/request`,
    LIST_ANSIBLE_HOSTS_RESPONSE: `${NAMESPACE}/hosts/response`
};

export const actions = {
    getAnsibleStats: (instanceId: ConcordId): GetAnsibleStatsRequest => ({
        type: actionTypes.GET_ANSIBLE_STATS_REQUEST,
        instanceId
    }),

    listAnsibleHosts: (instanceId: ConcordId, filter: SearchFilter): ListAnsibleHostsRequest => ({
        type: actionTypes.LIST_ANSIBLE_HOSTS_REQUEST,
        instanceId,
        filter
    }),

    reset: () => ({
        type: actionTypes.GET_ANSIBLE_STATS_RESET
    })
};

const getAnsibleStatsReducers = combineReducers<AnsibleStatsState>({
    running: makeLoadingReducer(
        [actionTypes.GET_ANSIBLE_STATS_REQUEST],
        [actionTypes.GET_ANSIBLE_STATS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.GET_ANSIBLE_STATS_REQUEST],
        [actionTypes.GET_ANSIBLE_STATS_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.GET_ANSIBLE_STATS_RESPONSE,
        actionTypes.GET_ANSIBLE_STATS_RESET
    )
});

const listAnsibleHostsReducers = combineReducers<AnsibleHostsState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_ANSIBLE_HOSTS_REQUEST],
        [actionTypes.LIST_ANSIBLE_HOSTS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.LIST_ANSIBLE_HOSTS_REQUEST],
        [actionTypes.LIST_ANSIBLE_HOSTS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.LIST_ANSIBLE_HOSTS_RESPONSE)
});

const lastFilterReducer = (state = {}, action: ListAnsibleHostsRequest) => {
    if (action.type === actionTypes.LIST_ANSIBLE_HOSTS_REQUEST) {
        return action.filter;
    }
    return state;
};

export const reducers = combineReducers<State>({
    stats: getAnsibleStatsReducers,
    hosts: listAnsibleHostsReducers,
    lastFilter: lastFilterReducer
});

export const selectors = {
    ansibleStats: (state: State): AnsibleStatsEntry => {
        const emptyData = { uniqueHosts: 0, hostGroups: [], stats: {} };
        return state.stats.response ? state.stats.response.data : emptyData;
    },

    ansibleHosts: (state: State): AnsibleHost[] => {
        return state.hosts.response ? state.hosts.response.data : [];
    },

    ansibleHostsNext: (state: State) => {
        return state.hosts.response ? state.hosts.response.next : undefined;
    },

    ansibleHostsPrev: (state: State) => {
        return state.hosts.response ? state.hosts.response.prev : undefined;
    }
};

function* onGetAnsibleStats({ instanceId }: GetAnsibleStatsRequest) {
    try {
        const response = yield call(apiGetAnsibleStats, instanceId);
        yield put({
            type: actionTypes.GET_ANSIBLE_STATS_RESPONSE,
            data: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.GET_ANSIBLE_STATS_RESPONSE, e);
    }
}

function* onListAnsibleHosts({ instanceId, filter }: ListAnsibleHostsRequest) {
    try {
        const limit = (filter && filter.limit) || ANSIBLE_HOST_LIMIT;
        const response = yield call(apiListAnsibleHosts, instanceId, { ...filter, limit });
        yield put({
            type: actionTypes.LIST_ANSIBLE_HOSTS_RESPONSE,
            data: response.items,
            next: response.next,
            prev: response.prev
        });
    } catch (e) {
        yield handleErrors(actionTypes.LIST_ANSIBLE_HOSTS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.GET_ANSIBLE_STATS_REQUEST, onGetAnsibleStats),
        takeLatest(actionTypes.LIST_ANSIBLE_HOSTS_REQUEST, onListAnsibleHosts)
    ]);
};
