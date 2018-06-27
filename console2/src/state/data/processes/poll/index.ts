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
import { delay } from 'redux-saga';
import { all, call, cancel, fork, put, race, take, takeLatest } from 'redux-saga/effects';

import { ConcordId } from '../../../../api/common';
import {
    get as apiGet,
    isFinal,
    list as apiListForms,
    listEvents as apiListEvents,
    ProcessEntry
} from '../../../../api/process';
import { ProcessEventEntry } from '../../../../api/process/event';
import { FormListEntry } from '../../../../api/process/form';
import { handleErrors, makeErrorReducer, makeLoadingReducer } from '../../common';
import {
    ProcessEventChunk,
    ProcessEvents,
    ProcessPollResponse,
    ProcessPollState,
    StartProcessPolling,
    State
} from './types';

const NAMESPACE = 'processes/poll';

const actionTypes = {
    START_PROCESS_POLLING: `${NAMESPACE}/poll/start`,
    STOP_PROCESS_POLLING: `${NAMESPACE}/poll/stop`,

    PROCESS_POLL_REQUEST: `${NAMESPACE}/poll/request`,
    PROCESS_POLL_RESPONSE: `${NAMESPACE}/poll/response`,
    FORCE_PROCESS_POLL: `${NAMESPACE}/poll/force`,

    RESET_PROCESS_POLL: `${NAMESPACE}/reset`
};

export const actions = {
    startProcessPolling: (instanceId: ConcordId): StartProcessPolling => ({
        type: actionTypes.START_PROCESS_POLLING,
        instanceId
    }),

    stopProcessPolling: () => ({
        type: actionTypes.STOP_PROCESS_POLLING
    }),

    pollRequest: () => ({
        type: actionTypes.PROCESS_POLL_REQUEST
    }),

    pollResponse: (
        process: ProcessEntry,
        forms?: FormListEntry[],
        events?: ProcessEventChunk
    ): ProcessPollResponse => ({
        type: actionTypes.PROCESS_POLL_RESPONSE,
        process,
        forms,
        events
    }),

    forcePoll: () => ({
        type: actionTypes.FORCE_PROCESS_POLL
    }),

    reset: () => ({
        type: actionTypes.RESET_PROCESS_POLL
    })
};

const currentRequestReducers = combineReducers<ProcessPollState>({
    running: makeLoadingReducer(
        [actionTypes.PROCESS_POLL_REQUEST],
        [actionTypes.PROCESS_POLL_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.PROCESS_POLL_REQUEST],
        [actionTypes.PROCESS_POLL_RESPONSE]
    ),
    response: (state = null, action: ProcessPollResponse) => {
        switch (action.type) {
            case actionTypes.PROCESS_POLL_RESPONSE:
                return action;
            default:
                return state;
        }
    }
});

const formsReducer: Reducer<FormListEntry[]> = (
    state = [],
    { type, error, forms }: ProcessPollResponse
) => {
    switch (type) {
        case actionTypes.RESET_PROCESS_POLL:
            return [];
        case actionTypes.PROCESS_POLL_RESPONSE:
            if (error) {
                return state;
            }

            // just replace the existing data
            if (!forms) {
                return [];
            }
            return forms;
        default:
            return state;
    }
};

const eventByIdReducer: Reducer<ProcessEvents> = (
    state = {},
    { type, error, events }: ProcessPollResponse
) => {
    switch (type) {
        case actionTypes.RESET_PROCESS_POLL:
            return {};
        case actionTypes.PROCESS_POLL_RESPONSE:
            if (error || !events || !events.data) {
                return state;
            }

            const result = events.replace ? {} : { ...state };
            events.data.forEach((e) => (result[e.id] = e));
            return result;
        default:
            return state;
    }
};

export const reducers = combineReducers<State>({
    currentRequest: currentRequestReducers,
    forms: formsReducer,
    eventById: eventByIdReducer
});

// enforce types
type BatchData = [FormListEntry[], Array<ProcessEventEntry<{}>>];

function* loadAll(process: ProcessEntry) {
    const { instanceId } = process;

    const [forms, events]: BatchData = yield all([
        call(apiListForms, instanceId),
        call(apiListEvents, instanceId)
    ]);

    yield put(actions.pollResponse(process, forms, { replace: true, data: events }));
}

function* doPoll(instanceId: ConcordId) {
    let lastEventTimestamp = null;

    try {
        while (true) {
            yield put(actions.pollRequest());

            // get the process' status
            const process = yield call(apiGet, instanceId);

            if (isFinal(process.status)) {
                // the process is completed, load everything
                // TODO probably unnecessary? or just try loading new events once
                yield loadAll(process);
                return;
            }

            // the process is still running, load the next chunk of data
            const [forms, events]: BatchData = yield all([
                call(apiListForms, instanceId),
                call(apiListEvents, instanceId, lastEventTimestamp, 100) // TODO constants
            ]);

            // get the last timestamp of the received events, it will be used to fetch the next data
            if (events && events.length > 0) {
                lastEventTimestamp = events[events.length - 1].eventDate;
            }

            // process the received data, append the events
            yield put(actions.pollResponse(process, forms, { replace: false, data: events }));

            yield race({
                delay: call(delay, 5000), // TODO constants
                forceRefresh: take(actionTypes.FORCE_PROCESS_POLL)
            });
        }
    } catch (e) {
        yield handleErrors(actionTypes.PROCESS_POLL_RESPONSE, e);
    }
}

function* onStartPolling({ instanceId }: StartProcessPolling) {
    yield put(actions.reset());

    const task = yield fork(doPoll, instanceId);
    yield take(actionTypes.STOP_PROCESS_POLLING);
    yield cancel(task);
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.START_PROCESS_POLLING, onStartPolling)]);
};
