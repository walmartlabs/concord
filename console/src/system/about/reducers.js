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