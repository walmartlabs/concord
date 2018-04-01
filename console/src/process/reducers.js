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
// @ts-check
import type { ConcordId } from '../types';
import { combineReducers } from 'redux';
import types from './actions';
import { createSelector } from 'reselect';
import * as _ from 'lodash';

const data = (state: any = null, { type, response, error }: any) => {
    switch (type) {
        case types.PROCESS_INFO_RESPONSE:
            if (error) {
                return state;
            }
            return response;
        case types.CLEAR_DATA:
            return null;
        default:
            return state;
    }
};

const polling = (state: any = null, { type }: any) => {
    switch (type) {
        case types.START_POLL_FOR_DATA:
            return true;
        case types.STOP_POLL_FOR_DATA:
            return false;
        case types.POLL_FOR_DATA_FAILURE:
            return false;
        default:
            return state;
    }
};

const loading = (state = false, { type }: any) => {
    switch (type) {
        case types.PROCESS_INFO_REQUEST:
            return true;
        case types.PROCESS_INFO_RESPONSE:
            return false;
        default:
            return state;
    }
};

const error = (state: any = null, { type, error, message }: any) => {
    switch (type) {
        case types.PROCESS_INFO_RESPONSE:
            if (!error) {
                return null;
            }
            return message;
        default:
            return state;
    }
};

const inFlight = (state: any = {}, { type, instanceId }: any) => {
    switch (type) {
        case types.PROCESS_KILL_REQUEST: {
            const o = {};
            o[instanceId] = true;
            return Object.assign({}, state, o);
        }
        case types.PROCESS_KILL_RESPONSE: {
            // TODO remove the key
            const o = {};
            o[instanceId] = false;
            return Object.assign({}, state, o);
        }
        default:
            return state;
    }
};

const events = combineReducers({
    data: (state: any = [], { type, data }: any) => {
        switch (type) {
            case types.EVENT_DATA_RESPONSE:
                return data || [];
            case types.STOP_POLL_FOR_DATA:
                return [];
            case types.EVENT_DATA_ADD:
                return _.uniqBy(state.concat(data), (e) => {
                    return e.id;
                });
            default:
                return state;
        }
    },
    error: (state: any = null, { type, error, message }: any) => {
        switch (type) {
            case types.EVENT_DATA_RESPONSE:
                if (!error) {
                    return null;
                }
                return message;
            case types.STOP_POLL_FOR_DATA:
                return null;
            default:
                return state;
        }
    }
});

const ansibleEventSummary = combineReducers({
    hosts: (state: any = [], { type, hosts }: any) => {
        switch (type) {
            case types.ansible_event_summary.SET_HOST_LIST:
                return hosts.sort();
            case types.STOP_POLL_FOR_DATA:
                return [];
            default:
                return state;
        }
    },
    queueFilter: (state: any = 'all', { type, status }: any) => {
        switch (type) {
            case types.ansible_event_summary.SET_QUEUE_FILTER:
                return status;
            case types.ansible_event_summary.CLEAR_QUEUE_FILTER:
                return 'all';
            default:
                return state;
        }
    },
    selectedAnsibleEvents: (state: any = [], { type, events }: any) => {
        switch (type) {
            case types.ansible_event_summary.SELECTED_ANSIBLE_EVENTS:
                return events;
            case types.STOP_POLL_FOR_DATA:
                return [];
            default:
                return state;
        }
    },
    selectedHost: (state: any = null, { type, hostname, hosts }: any) => {
        switch (type) {
            case types.ansible_event_summary.SET_SELECTED_HOST:
                return hostname;
            case types.ansible_event_summary.SET_HOST_LIST:
                return hosts[0];
            case types.STOP_POLL_FOR_DATA:
                return null;
            default:
                return state;
        }
    }
});

const ansibleStats = combineReducers({
    data: (state: any = null, { type, data }: any) => {
        switch (type) {
            case types.ANSIBLE_STATS_RESPONSE:
                return data || null;
            case types.STOP_POLL_FOR_DATA:
                return null;
            default:
                return state;
        }
    },
    error: (state: any = null, { type, error, message }: any) => {
        switch (type) {
            case types.ANSIBLE_STATS_RESPONSE:
                if (!error) {
                    return null;
                }
                return message;
            default:
                return state;
        }
    }
});

const byProject = (state: any = null, { type, data }: any) => {
    switch (type) {
        case types.PROCESS_BY_PROJECT_RESPONSE:
            return data;
        case types.PROCESS_BY_PROJECT_ERROR:
            return null;
        default:
            return state;
    }
};

export default combineReducers({
    data,
    loading,
    error,
    inFlight,
    polling,
    events,
    ansibleStats,
    ansibleEventSummary,
    byProject
});

export const selectors = {
    getData: (state: any) => state.data,
    isLoading: (state: any) => state.loading,
    getError: (state: any) => state.error,
    isInFlight: (state: any, instanceId: ConcordId) => state && state.inFlight[instanceId] === true,
    pollingData: (state: any) => state.events.polling,
    getEvents: (state: any) => state.events.data,
    ansibleStatsSelector: (state: any) => state.ansibleStats,
    ansibleEventSummary: {
        hosts: (state: any) => state.ansibleEventSummary.hosts,
        queueFilter: (state: any) => state.ansibleEventSummary.queueFilter,
        selectedAnsibleEvents: (state: any) => state.ansibleEventSummary.selectedAnsibleEvents,
        selectedHost: (state: any) => state.ansibleEventSummary.selectedHost
    },
    get getEventsByType() {
        return createSelector(this.getEvents, (events) => {
            return _.groupBy(events, (d) => d.eventType);
        });
    },
    get getEventsByHostName() {
        return createSelector(this.getEvents, (events) => {
            return _.groupBy(events, (d) => d.data.host);
        });
    },
    get getHostNames() {
        return createSelector(this.getEvents, (events) => {
            return Object.keys(_.groupBy(events, (d) => d.data.host));
        });
    },
    get getHostEventsByTaskName() {
        return createSelector(this.getEvents, (events) => {
            return _.groupBy(events, (d) => d.data.task);
        });
    },
    get getEventsByStatus() {
        return createSelector(this.getEvents, (events) => {
            return _.groupBy(events, (d) => d.data.status);
        });
    }
};
