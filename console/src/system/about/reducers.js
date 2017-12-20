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

const info = (state = {}, action) => {
    switch (action.type) {
        case types.ABOUT_INFO_RESPONSE:
            return action.response;
        default:
            return state;
    }
};

const error = (state = null, {type, error, message}) => {
    switch (type) {
        case types.ABOUT_INFO_RESPONSE:
            if (error) {
                return message;
            }
            return null;
        default:
            return state;
    }
};

const loading = (state = false, {type}: any) => {
    switch (type) {
        case types.ABOUT_INFO_REQUEST:
            return true;
        case types.ABOUT_INFO_RESPONSE:
            return false;
        default:
            return state;
    }
};

export default combineReducers({info, error, loading});

export const getInfo = (state: any) => state.info;
export const getError = (state: any) => state.error;
export const isLoading = (state: any) => state.loading;
