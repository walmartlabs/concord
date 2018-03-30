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
import { combineReducers } from 'redux';
import types from './actions';

const result = (state: any = null, { type, error, response }: any) => {
    switch (type) {
        case types.PROJECT_START_RESPONSE:
            return { ...response, error };
        case types.PROJECT_START_REQUEST:
        case types.PROJECT_START_RESET:
            return null;
        default:
            return state;
    }
};

const loading = (state = false, { type }: any) => {
    switch (type) {
        case types.PROJECT_START_REQUEST:
            return true;
        case types.PROJECT_START_RESPONSE:
        case types.PROJECT_START_RESET:
            return false;
        default:
            return state;
    }
};

export default combineReducers({ result, loading });

export const getResult = (state: any) => state.result;
export const isLoading = (state: any) => state.loading;
