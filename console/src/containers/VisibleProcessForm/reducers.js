// @flow
import {combineReducers} from "redux";
import {actionTypes} from "./actions";
import * as common from "../../reducers/common";

const data = (state = null, action) => {
    switch (action.type) {
        case actionTypes.FETCH_PROCESS_FORM_RESULT:
            if (action.error) {
                return state;
            }
            return action.response;
        case actionTypes.SUBMIT_PROCESS_FORM_RESULT:
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
        case actionTypes.FETCH_PROCESS_FORM_REQUEST:
        case actionTypes.FETCH_PROCESS_FORM_RESULT:
        case actionTypes.SUBMIT_PROCESS_FORM_REQUEST:
            return false;
        case actionTypes.SUBMIT_PROCESS_FORM_RESULT:
            if (action.response && !action.response.errors) {
                return true;
            }
            return state;
        default:
            return state;
    }
};

const loading = common.makeBooleanTriggerReducer(actionTypes.FETCH_PROCESS_FORM_REQUEST, actionTypes.FETCH_PROCESS_FORM_RESULT);
const submitting = common.makeBooleanTriggerReducer(actionTypes.SUBMIT_PROCESS_FORM_REQUEST, actionTypes.SUBMIT_PROCESS_FORM_RESULT);
const fetchError = common.makeErrorReducer(actionTypes.FETCH_PROCESS_FORM_REQUEST, actionTypes.FETCH_PROCESS_FORM_RESULT);
const submitError = common.makeErrorReducer(actionTypes.SUBMIT_PROCESS_FORM_REQUEST, actionTypes.SUBMIT_PROCESS_FORM_RESULT);

export default combineReducers({data, loading, submitting, fetchError, submitError, completed});

export const getData = (state: any) => state.data;
export const getIsLoading = (state: any) => state.loading;
export const getIsSubmitting = (state: any) => state.submitting;
export const getFetchError = (state: any) => state.fetchError;
export const getSubmitError = (state: any) => state.submitError;
export const getIsCompleted = (state: any) => state.completed;

