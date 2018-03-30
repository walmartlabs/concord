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

const submitting = (state = false, action) => {
    switch (action.type) {
        case types.LOGIN_REQUEST:
            return true;
        case types.LOGIN_RESPONSE:
            return false;
        default:
            return state;
    }
};

const error = (state = null, { type, error, message }) => {
    switch (type) {
        case types.LOGIN_RESPONSE:
            if (error) {
                return message;
            }
            return null;
        default:
            return state;
    }
};

const loggedIn = (state = false, { type, error }) => {
    switch (type) {
        case types.LOGIN_RESPONSE:
            if (error) {
                return false;
            }
            return true;
        default:
            return state;
    }
};

export default combineReducers({ submitting, error, loggedIn });

export const isSubmitting = (state: any) => state.submitting;
export const getError = (state: any) => state.error;
export const isLoggedIn = (state: any) => state.loggedIn;
