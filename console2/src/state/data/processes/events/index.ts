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
import { all, call, put, takeLatest } from 'redux-saga/effects';

import { handleErrors, makeErrorReducer, makeLoadingReducer } from '../../common';
import { AnsibleEvents, GetAnsibleEventsRequest, GetAnsibleEventsResponse, State } from './types';
import {
    AnsibleStatus,
    listAnsibleEvents as apiListAnsibleEvents
} from '../../../../api/process/ansible';
import { ConcordId } from '../../../../api/common';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'processes/events';

const actionTypes = {
    GET_ANSIBLE_EVENTS_REQUEST: `${NAMESPACE}/ansibleEvents/request`,
    GET_ANSIBLE_EVENTS_RESPONSE: `${NAMESPACE}/ansibleEvents/response`
};

export const actions = {
    listAnsibleEvents: (
        instanceId: ConcordId,
        host?: string,
        hostGroup?: string,
        status?: AnsibleStatus
    ): GetAnsibleEventsRequest => ({
        type: actionTypes.GET_ANSIBLE_EVENTS_REQUEST,
        instanceId,
        host,
        hostGroup,
        status
    })
};

const ansibleEvents: Reducer<AnsibleEvents> = (
    state = {},
    { type, error, events }: GetAnsibleEventsResponse
) => {
    switch (type) {
        case actionTypes.GET_ANSIBLE_EVENTS_RESPONSE:
            if (error || !events) {
                return {};
            }

            const result = {};
            events.forEach((o) => {
                result[o.id] = o;
            });
            return result;
        default:
            return state;
    }
};

const loading = makeLoadingReducer(
    [actionTypes.GET_ANSIBLE_EVENTS_REQUEST],
    [actionTypes.GET_ANSIBLE_EVENTS_RESPONSE]
);

const listError = makeErrorReducer(
    [actionTypes.GET_ANSIBLE_EVENTS_REQUEST],
    [actionTypes.GET_ANSIBLE_EVENTS_RESPONSE]
);

export const reducers = combineReducers<State>({
    ansibleEvents,
    loading,
    error: listError
});

function* onAnsibleEvents({ instanceId, host, hostGroup, status }: GetAnsibleEventsRequest) {
    try {
        const response = yield call(apiListAnsibleEvents, instanceId, host, hostGroup, status);
        yield put({
            type: actionTypes.GET_ANSIBLE_EVENTS_RESPONSE,
            events: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.GET_ANSIBLE_EVENTS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.GET_ANSIBLE_EVENTS_REQUEST, onAnsibleEvents)]);
};
