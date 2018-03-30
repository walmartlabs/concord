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
import type { ConcordId } from '../types';

const NAMESPACE = 'process';

const types = {
    PROCESS_INFO_REQUEST: `${NAMESPACE}/info/request`,
    PROCESS_INFO_RESPONSE: `${NAMESPACE}/info/response`,

    PROCESS_KILL_REQUEST: `${NAMESPACE}/kill/request`,
    PROCESS_KILL_RESPONSE: `${NAMESPACE}/kill/response`,

    EVENT_DATA_REQUEST: `${NAMESPACE}/events/request`,
    EVENT_DATA_RESPONSE: `${NAMESPACE}/events/response`,
    EVENT_DATA_ADD: `${NAMESPACE}/events/add`,

    ANSIBLE_STATS_REQUEST: `${NAMESPACE}/ansible_stats/request`,
    ANSIBLE_STATS_RESPONSE: `${NAMESPACE}/ansible_stats/response`,

    PROCESS_BY_PROJECT_REQUEST: `${NAMESPACE}/by_project/request`,
    PROCESS_BY_PROJECT_RESPONSE: `${NAMESPACE}/by_project/response`,
    PROCESS_BY_PROJECT_ERROR: `${NAMESPACE}/by_project/error`,

    START_POLLING_WATCHER: `${NAMESPACE}/background_polling/start`,
    START_POLL_FOR_DATA: `${NAMESPACE}/data_poll/start`,
    STOP_POLL_FOR_DATA: `${NAMESPACE}/data_poll/stop`,
    POLL_FOR_DATA_FAILURE: `${NAMESPACE}/data_poll/failure`,

    CLEAR_DATA: `${NAMESPACE}/data/clear`,

    // Ansible Event Summary Actions
    ansible_event_summary: {
        SET_HOST_LIST: `${NAMESPACE}/AnsibleEventSummary/hostList [SET]`,
        CLEAR_HOST_LIST: `${NAMESPACE}/AnsibleEventSummary/hostlist [CLEAR]`,
        SET_SELECTED_ANSIBLE_EVENTS: `${NAMESPACE}/AnsibleEventSummary/selectedAnsibleEvents [SET]`,
        CLEAR_SELECTED_ANSIBLE_EVENTS: `${NAMESPACE}/AnsibleEventSummary/selectedAnsibleEvents [CLEAR]`,
        SET_SELECTED_HOST: `${NAMESPACE}/AnsibleEventSummary/selectedHost [SET]`,
        CLEAR_SELECTED_HOST: `${NAMESPACE}/AnsibleEventSummary/selectedHost [CLEAR]`,
        SET_QUEUE_FILTER: `${NAMESPACE}/queueFilter [SET]`,
        CLEAR_QUEUE_FILTER: `${NAMESPACE}/queueFilter [CLEAR]`
    }
};

export default types;

export const load = (instanceId: ConcordId) => ({
    type: types.PROCESS_INFO_REQUEST,
    instanceId
});

export const requestProjectProcesses = (projectId: string) => ({
    type: types.PROCESS_BY_PROJECT_REQUEST,
    projectId
});

export const responseProjectProcesses = (projectId: string, data: any) => ({
    type: types.PROCESS_BY_PROJECT_RESPONSE,
    projectId,
    data
});

export const errorProjectProcesses = (projectId: string, message: string) => ({
    type: types.PROCESS_BY_PROJECT_ERROR,
    projectId,
    error: true,
    message
});

export const kill = (instanceId: ConcordId, onSuccess: Array<any>) => ({
    type: types.PROCESS_KILL_REQUEST,
    instanceId,
    onSuccess
});

export const requestEventData = (instanceId: ConcordId, limit: string, after: string) => ({
    type: types.EVENT_DATA_REQUEST,
    instanceId,
    limit: limit || '',
    after: after || ''
});

export const storeEventData = (instanceId: ConcordId, data: any) => ({
    type: types.EVENT_DATA_RESPONSE,
    instanceId,
    data
});

export const addEvents = (data: any) => ({
    type: types.EVENT_DATA_ADD,
    data
});

export const requestAnsibleStats = (instanceId: ConcordId) => ({
    type: types.ANSIBLE_STATS_REQUEST,
    instanceId
});

export const storeAnsibleStats = (instanceId: ConcordId, data: any) => ({
    type: types.ANSIBLE_STATS_RESPONSE,
    instanceId,
    data
});

export const startPollingWatcher = (instanceId: ConcordId) => ({
    type: types.START_POLLING_WATCHER,
    instanceId
});

export const startPolling = () => ({
    type: types.START_POLL_FOR_DATA
});

export const stopPolling = () => ({
    type: types.STOP_POLL_FOR_DATA
});

export const pollingFailure = (message: string) => ({
    type: types.POLL_FOR_DATA_FAILURE,
    message,
    error: true
});

export const clearData = () => ({
    type: types.CLEAR_DATA
});

export const ansible_event_summary = {
    set_host_list: (hosts: string[]) => {
        return {
            type: types.ansible_event_summary.SET_HOST_LIST,
            hosts
        };
    },
    clear_host_list: () => ({
        type: types.ansible_event_summary.CLEAR_HOST_LIST
    }),
    set_selected_ansible_events: (events: string[]) => ({
        type: types.ansible_event_summary.SET_SELECTED_ANSIBLE_EVENTS,
        events: events
    }),
    clear_selected_ansible_events: () => ({
        type: types.ansible_event_summary.CLEAR_SELECTED_ANSIBLE_EVENTS
    }),
    set_selected_host: (hostname: string) => ({
        type: types.ansible_event_summary.SET_SELECTED_HOST,
        hostname: hostname
    }),
    clear_selected_host: () => ({
        type: types.ansible_event_summary.CLEAR_SELECTED_HOST
    }),
    setQueueFilter: (status: string) => ({
        type: types.ansible_event_summary.SET_QUEUE_FILTER,
        status
    }),
    clearQueueFilter: () => ({
        type: types.ansible_event_summary.CLEAR_QUEUE_FILTER
    })
};
