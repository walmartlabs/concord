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
import * as common from "../../reducers/common";
import types from "./actions";

const data = (state = null, action) => {
    switch (action.type) {
        case types.PROCESS_FORM_RESPONSE:
            if (action.error) {
                return state;
            }
            return action.response;
        case types.PROCESS_FORM_SUBMIT_RESPONSE:
            const newData = action.response;
            if (!newData) {
                return state;
            }

            const newState = {...state, ...newData};

            // if the server returned no errors, let's clear the errors in the state
            if (!newData.errors) {
                newState.errors = undefined;
            }

            return newState;
        default:
            return state;
    }
};

const completed = (state = false, action) => {
    switch (action.type) {
        case types.PROCESS_FORM_REQUEST:
        case types.PROCESS_FORM_RESPONSE:
        case types.PROCESS_FORM_SUBMIT_REQUEST:
            return false;
        case types.PROCESS_FORM_SUBMIT_RESPONSE:
            if (action.response && !action.response.errors) {
                return true;
            }
            return state;
        default:
            return state;
    }
};

const loading = common.booleanTrigger(types.PROCESS_FORM_REQUEST, types.PROCESS_FORM_RESPONSE);
const submitting = common.booleanTrigger(types.PROCESS_FORM_SUBMIT_REQUEST, types.PROCESS_FORM_SUBMIT_RESPONSE);
const fetchError = common.error(types.PROCESS_FORM_RESPONSE);
const submitError = common.error(types.PROCESS_FORM_SUBMIT_RESPONSE);

export default combineReducers({data, loading, submitting, fetchError, submitError, completed});

export const getData = (state: any) => state.data;
export const getIsLoading = (state: any) => state.loading;
export const getIsSubmitting = (state: any) => state.submitting;
export const getFetchError = (state: any) => state.fetchError;
export const getSubmitError = (state: any) => state.submitError;
export const getIsCompleted = (state: any) => state.completed;

