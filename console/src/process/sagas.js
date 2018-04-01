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
import {
    call,
    fork,
    put,
    takeLatest,
    all,
    take,
    select,
    cancel,
    cancelled
} from 'redux-saga/effects';
import { delay } from 'redux-saga';
import types, { pollingFailure, responseProjectProcesses, errorProjectProcesses } from './actions';
import * as constants from './constants';
import * as api from './api';
import * as queueApi from './queue/api';

const STATUS_REFRESH_DELAY = 5000;

function* loadProcessData(action: any): Generator<*, *, *> {
    const { instanceId } = action;

    try {
        const [status, forms] = yield all([
            call(api.fetchStatus, instanceId),
            call(api.listForms, instanceId)
        ]);

        yield put({
            type: types.PROCESS_INFO_RESPONSE,
            instanceId,
            response: {
                ...status,
                forms
            }
        });
    } catch (e) {
        yield put({
            type: types.PROCESS_INFO_RESPONSE,
            instanceId,
            error: true,
            message: e.message || 'Error while loading a process info'
        });
    }
}

function* loadProjectProcessData(action: any): Generator<*, *, *> {
    const { projectId } = action;
    try {
        const processes = yield call(queueApi.fetchProjectProcesses, projectId);

        yield put(responseProjectProcesses(projectId, processes));

        yield call(delay, STATUS_REFRESH_DELAY);
    } catch (e) {
        yield put(errorProjectProcesses(projectId, e.message));
    }
}

function* kill(action: any): Generator<*, *, *> {
    const { instanceId, onSuccess } = action;

    try {
        yield call(api.kill, instanceId);

        yield put({
            type: types.PROCESS_KILL_RESPONSE,
            instanceId
        });

        if (onSuccess) {
            for (const a of onSuccess) {
                yield put(a);
            }
        }
    } catch (e) {
        yield put({
            type: types.PROCESS_KILL_RESPONSE,
            instanceId,
            error: true,
            message: e.message || 'Error while killing a process'
        });
    }
}

function* loadEventData(action: any): Generator<*, *, *> {
    const { instanceId, limit, after } = action;
    try {
        const response = yield call(api.fetchConcordEvents, instanceId, limit, after);
        yield put({ type: types.EVENT_DATA_ADD, data: response });
    } catch (e) {
        yield put({
            type: types.EVENT_DATA_RESPONSE,
            errorRetrievingEvents: true,
            message: e.message || 'Error while retrieving process event data'
        });
    }
}

function* loadAnsibleStats(action: any): Generator<*, *, *> {
    const { instanceId } = action;
    try {
        const response = yield call(api.fetchAnsibleStats, instanceId);
        yield put({ type: types.ANSIBLE_STATS_RESPONSE, data: response });
    } catch (e) {
        yield put({
            type: types.ANSIBLE_STATS_RESPONSE,
            errorRetrievingAnsibleStats: true,
            message: e.message || 'Issue Retrieving ansible_stats attachment'
        });
    }
}

// TODO: Refactor into separate parallel sagas
export function* startPollingData(instanceId: string): Generator<*, *, *> {
    try {
        const actionState = {
            pollStatus: true,
            pollAnsibleStats: true,
            pollEvents: true,
            pullAllEvents: false,
            statusIsFinalized: false
        };

        // TODO: make DRY
        // Pull status data out of state
        const { status } = yield select(({ process }) => process.data);

        actionState.statusIsFinalized = constants.finalStatuses.includes(status);

        // Check if we should poll events or pull all events
        if (actionState.statusIsFinalized) {
            actionState.pollEvents = false;
            actionState.pullAllEvents = true;
        }

        while (true) {
            if (
                actionState.pollStatus === false &&
                actionState.pollAnsibleStats === false &&
                actionState.pollEvents === false &&
                actionState.pullAllEvents === false &&
                actionState.statusIsFinalized
            ) {
                break; // break out of the loop and stop polling for data
            }

            // Query status data
            if (actionState.pollStatus) {
                yield fork(loadProcessData, { instanceId });
            }

            // TODO: make DRY
            // Pull status data out of state
            const { status } = yield select(({ process }) => process.data);

            actionState.statusIsFinalized = constants.finalStatuses.includes(status);

            // Check if process is Finalized
            if (actionState.statusIsFinalized) {
                actionState.pollStatus = false;
            }

            // Poll Ansible Stats one time if process is finalized
            // and if we haven't done so already
            if (actionState.statusIsFinalized && actionState.pollAnsibleStats) {
                yield fork(loadAnsibleStats, { instanceId });
                actionState.pollAnsibleStats = false;
            }

            // PollEvents incrementally if status is not Finalized
            // Else poll All events at once one time if status is Finalized
            if (actionState.pollEvents) {
                let timestamp = null;

                let events = yield select(({ process }) => process.events.data);

                if (events.length > 0) {
                    timestamp = events[events.length - 1].eventDate;
                }

                yield fork(loadEventData, {
                    instanceId,
                    limit: '100',
                    after: timestamp || null
                });

                // Check if we should poll events or pull all events
                if (actionState.statusIsFinalized) {
                    actionState.pollEvents = false;
                }
            } else if (actionState.pullAllEvents) {
                yield fork(loadEventData, { instanceId });
                // ensures only pulling all events only one time
                actionState.pullAllEvents = false;
            }

            // Delay Saga for some time
            yield call(delay, STATUS_REFRESH_DELAY);
        }
    } catch (e) {
        yield put(pollingFailure(`Polling Error! ${JSON.stringify(e)}`));
    } finally {
        if (yield cancelled()) {
            yield put(pollingFailure('Polling Stopped!'));
        }
    }
}

export function* process_poller({ instanceId }) {
    yield take(types.START_POLL_FOR_DATA);
    const pollingTask = yield fork(startPollingData, instanceId);
    yield take(types.STOP_POLL_FOR_DATA);
    yield cancel(pollingTask);
}

export default function*(): Generator<*, *, *> {
    yield all([
        fork(takeLatest, types.PROCESS_INFO_REQUEST, loadProcessData),
        fork(takeLatest, types.PROCESS_KILL_REQUEST, kill),
        fork(takeLatest, types.EVENT_DATA_REQUEST, loadEventData),
        fork(takeLatest, types.ANSIBLE_STATS_REQUEST, loadAnsibleStats),
        fork(takeLatest, types.START_POLLING_WATCHER, process_poller),
        fork(takeLatest, types.PROCESS_BY_PROJECT_REQUEST, loadProjectProcessData)
    ]);
}
