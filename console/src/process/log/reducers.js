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
import {combineReducers} from "redux";
import types from "./actions";

const data = (state = [], action) => {
    switch (action.type) {
        case types.PROCESS_LOG_REQUEST:
            if (action.error) {
                return state;
            }
            if (action.reset) {
                return [];
            }
            return state;
        case types.PROCESS_LOG_RESPONSE:
            return [...state, action.data];
        default:
            return state;
    }
};

const loading = (state = false, action) => {
    switch (action.type) {
        case types.PROCESS_LOG_REQUEST:
            return true;
        case types.PROCESS_LOG_RESPONSE:
            return false;
        default:
            return state;
    }
};

const error = (state: any = null, {type, error, message}: any) => {
    switch (type) {
        case types.PROCESS_LOG_RESPONSE:
            if (!error) {
                return null;
            }
            return message;
        default:
            return state;
    }
};

const range = (state = {}, action) => {
    switch (action.type) {
        case types.PROCESS_LOG_REQUEST:
            if (action.reset) {
                return {};
            }
            return state;
        case types.PROCESS_LOG_RESPONSE:
            if (action.error) {
                return state;
            }

            const a = action.range.low;
            const b = state.min === undefined ? a : state.min;
            const min = Math.min(a, b);
            return {...action.range, min};
        default:
            return state;
    }
};

const status = (state = null, action) => {
    switch (action.type) {
        case types.PROCESS_LOG_RESPONSE:
            if (action.error) {
                return state;
            }
            return action.status;
        default:
            return state;
    }
};

export default combineReducers({data, loading, error, range, status});

export const getData = (state: any) => state.data;
export const getIsLoading = (state: any) => state.loading;
export const getError = (state: any) => state.error;
export const getRange = (state: any) => state.range;
export const getStatus = (state: any) => state.status;
