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

const refreshResult = (state: any = null, {type, response, error}: any) => {
    switch (type) {
        case types.REPOSITORY_REFRESH_RESPONSE:
            if (error) {
                return false;
            }
            return response;
        case types.REPOSITORY_REFRESH_REQUEST:
        case types.REPOSITORY_REFRESH_RESET:
            return null;
        default:
            return state;
    }
};

const refreshing = (state = false, {type}: any) => {
    switch (type) {
        case types.REPOSITORY_REFRESH_REQUEST:
            return true;
        case types.REPOSITORY_REFRESH_RESPONSE:
        case types.REPOSITORY_REFRESH_RESET:
            return false;
        default:
            return state;
    }
};

const refreshError = (state: any = null, {type, error, message}: any) => {
    switch (type) {
        case types.REPOSITORY_REFRESH_RESPONSE:
            if (!error) {
                return null;
            }
            return message;
        case types.REPOSITORY_REFRESH_REQUEST:
        case types.REPOSITORY_REFRESH_RESET:
            return null;
        default:
            return state;
    }
};

export default combineReducers({refreshResult, refreshing, refreshError});

export const isRefreshing = (state: any) => state && state.refreshing;
export const getRefreshResult = (state: any) => state && state.refreshResult;
export const getRefreshError = (state: any) => state && state.refreshError;
