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
import { ConcordId } from '../../../../api/common';
import { list as apiList } from '../../../../api/process/attachment';
import {
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer
} from '../../common';
import { ListProcessAttachments, ListProcessAttachmentState, State } from './types';

const NAMESPACE = 'processes/attachments';

const actionTypes = {
    GET_PROCESS_ATTACHMENT_REQUEST: `${NAMESPACE}/request`,
    GET_PROCESS_ATTACHMENT_RESPONSE: `${NAMESPACE}/response`
};

export const actions = {
    listProcessAttachments: (instanceId: ConcordId): ListProcessAttachments => ({
        type: actionTypes.GET_PROCESS_ATTACHMENT_REQUEST,
        instanceId
    })
};

const listReducers = combineReducers<ListProcessAttachmentState>({
    running: makeLoadingReducer(
        [actionTypes.GET_PROCESS_ATTACHMENT_REQUEST],
        [actionTypes.GET_PROCESS_ATTACHMENT_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.GET_PROCESS_ATTACHMENT_REQUEST],
        [actionTypes.GET_PROCESS_ATTACHMENT_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.GET_PROCESS_ATTACHMENT_RESPONSE)
});

export const reducers = combineReducers<State>({
    list: listReducers
});

export const selectors = {
    processAttachments: (state: State) => {
        const attachments = state.list.response ? state.list.response.items : [];

        // filter out the system files
        return attachments.filter((attachment) => attachment.indexOf('_state') < 0);
    }
};

function* onList({ instanceId }: ListProcessAttachments) {
    try {
        const response = yield call(apiList, instanceId);
        yield put({
            type: actionTypes.GET_PROCESS_ATTACHMENT_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.GET_PROCESS_ATTACHMENT_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.GET_PROCESS_ATTACHMENT_REQUEST, onList)]);
};
