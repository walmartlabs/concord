// @flow
import {combineReducers} from "redux";
import types from "./actions";

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

const error = (state = null, {type, error, message}) => {
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

const loggedIn = (state = false, {type, error}) => {
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

export default combineReducers({submitting, error, loggedIn});

export const isSubmitting = (state: any) => state.submitting;
export const getError = (state: any) => state.error;
export const isLoggedIn = (state: any) => state.loggedIn;