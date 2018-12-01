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
import { all, call, cancel, fork, put, race, select, take, takeLatest } from 'redux-saga/effects';

import { ConcordId } from '../../../../api/common';
import {
    get as apiGet,
    isFinal,
    list as apiListForms,
    listEvents as apiListEvents,
    ProcessEntry
} from '../../../../api/process';
import { actions as ansibleActions } from '../ansible/index';
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
import { State as ProcessState } from '../types';
import { addMinutes, isAfter } from 'date-fns';

const NAMESPACE = 'processes/poll';
export const MAX_EVENT_COUNT = 5000;

const actionTypes = {
    START_PROCESS_POLLING: `${NAMESPACE}/poll/start`,
    STOP_PROCESS_POLLING: `${NAMESPACE}/poll/stop`,

    PROCESS_POLL_REQUEST: `${NAMESPACE}/poll/request`,
    PROCESS_POLL_RESPONSE: `${NAMESPACE}/poll/response`,
    FORCE_PROCESS_POLL: `${NAMESPACE}/poll/force`,

    RESET_PROCESS_POLL: `${NAMESPACE}/reset`
};

export const actions = {
    startProcessPolling: (instanceId: ConcordId, forceLoadAll?: boolean): StartProcessPolling => ({
        type: actionTypes.START_PROCESS_POLLING,
        instanceId,
        forceLoadAll
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
        events?: ProcessEventChunk,
        tooMuchData?: boolean
    ): ProcessPollResponse => ({
        type: actionTypes.PROCESS_POLL_RESPONSE,
        process,
        forms,
        events,
        tooMuchData
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

function* loadAll(process: ProcessEntry, forceLoadAll?: boolean) {
    const { instanceId } = process;

    const [forms, events]: BatchData = yield all([
        call(apiListForms, instanceId),
        call(
            apiListEvents,
            instanceId,
            'ELEMENT',
            process.createdAt,
            forceLoadAll ? null : MAX_EVENT_COUNT + 1
        )
    ]);

    yield put(ansibleActions.getAnsibleStats(instanceId));

    // get the last known filter
    const lastFilter = yield select(
        ({ processes }: { processes: ProcessState }) => processes.ansible.lastFilter
    );
    yield put(ansibleActions.listAnsibleHosts(instanceId, lastFilter));

    const tooMuchData = !forceLoadAll && events && events.length > MAX_EVENT_COUNT;
    yield put(actions.pollResponse(process, forms, { replace: true, data: events }, tooMuchData));
}

function* doPoll(instanceId: ConcordId, forceLoadAll?: boolean) {
    let lastEventTimestamp = null;

    try {
        while (true) {
            yield put(actions.pollRequest());

            // get the process' status
            const process = yield call(apiGet, instanceId);

            // because Ansible stats are calculated by an async process on the backend, we poll for
            // additional 10 minutes after the process finishes to make sure we got everything
            const hasntChangedRecently = isAfter(Date.now(), addMinutes(process.lastUpdatedAt, 10));

            if (isFinal(process.status) && hasntChangedRecently) {
                // the process is completed, load everything
                // TODO probably unnecessary? or just try loading new events once
                yield loadAll(process, forceLoadAll);
                return;
            }

            // the process is still running, load the next chunk of data
            const [forms, events]: BatchData = yield all([
                call(apiListForms, instanceId),
                call(apiListEvents, instanceId, 'ELEMENT', lastEventTimestamp, 100) // TODO constants
            ]);

            // get the last timestamp of the received events, it will be used to fetch the next data
            if (events && events.length > 0) {
                lastEventTimestamp = events[events.length - 1].eventDate;
            }

            // get the last known filter
            const lastFilter = yield select(
                ({ processes }: { processes: ProcessState }) => processes.ansible.lastFilter
            );

            yield put(ansibleActions.getAnsibleStats(instanceId));
            yield put(ansibleActions.listAnsibleHosts(instanceId, lastFilter));

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

function* onStartPolling({ instanceId, forceLoadAll }: StartProcessPolling) {
    yield put(actions.reset());
    yield put(ansibleActions.reset());

    const task = yield fork(doPoll, instanceId, forceLoadAll);
    yield take(actionTypes.STOP_PROCESS_POLLING);
    yield cancel(task);
}

export const sagas = function*() {
    yield all([takeLatest(actionTypes.START_PROCESS_POLLING, onStartPolling)]);
};
