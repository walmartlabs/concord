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
import type {ConcordId} from "../types";
import {combineReducers} from "redux";
import types from "./actions";

const data = (state: any = null, {type, response, error}: any) => {
    switch (type) {
        case types.PROCESS_INFO_RESPONSE:
            if (error) {
                return state;
            }
            return response;
        default:
            return state;
    }
};

const loading = (state = false, {type}: any) => {
    switch (type) {
        case types.PROCESS_INFO_REQUEST:
            return true;
        case types.PROCESS_INFO_RESPONSE:
            return false;
        default:
            return state;
    }
};

const error = (state: any = null, {type, error, message}: any) => {
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

const inFlight = (state: any = {}, {type, instanceId}: any) => {
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

export default combineReducers({data, loading, error, inFlight});

export const getData = (state: any) => state.data;
export const isLoading = (state: any) => state.loading;
export const getError = (state: any) => state.error;
export const isInFlight = (state: any, instanceId: ConcordId) => state && state.inFlight[instanceId] === true;
