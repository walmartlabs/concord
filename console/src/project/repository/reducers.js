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

const testResult = (state: any = null, { type, response, error }: any) => {
    switch (type) {
        case types.REPOSITORY_TEST_RESPONSE:
            if (error) {
                return false;
            }
            return response;
        case types.REPOSITORY_TEST_REQUEST:
        case types.REPOSITORY_TEST_RESET:
            return null;
        default:
            return state;
    }
};

const loading = (state = false, { type }: any) => {
    switch (type) {
        case types.REPOSITORY_TEST_REQUEST:
            return true;
        case types.REPOSITORY_TEST_RESPONSE:
        case types.REPOSITORY_TEST_RESET:
            return false;
        default:
            return state;
    }
};

const error = (state: any = null, { type, error, message }: any) => {
    switch (type) {
        case types.REPOSITORY_TEST_RESPONSE:
            if (!error) {
                return null;
            }
            return message;
        case types.REPOSITORY_TEST_REQUEST:
        case types.REPOSITORY_TEST_RESET:
            return null;
        default:
            return state;
    }
};
export default combineReducers({ testResult, loading, error });

export const getTestResult = (state: any) => state.testResult;
export const isLoading = (state: any) => state.loading;
export const getError = (state: any) => state.error;
